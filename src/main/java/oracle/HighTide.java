package oracle;
import oracle.bband.*;
import oracle.sinopac.*;
import java.text.*;
import oracle.common.*;
import java.util.*;
import java.io.*;
import org.jfree.ui.*;
import org.jfree.chart.*;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.data.xy.XYSeries;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import java.awt.BasicStroke;

public class HighTide {
    private SimpleDateFormat formatter = new SimpleDateFormat("HHmmss");
    private Vector<Transaction> transactions = new Vector<Transaction>();
    private int duration = ConfigurableParameters.KBAR_LENGTH;
    private int tolerance = ConfigurableParameters.LOST_TOLERANCE;
    private int lifecycle = ConfigurableParameters.TRANS_LIFECYCLE;
    private int minimalBoundSize = ConfigurableParameters.BBAND_BOUND_SIZE;
    private KBarBuilder kbarBuilder = new KBarBuilder(duration); // in millisecond
    private Vector<Transaction> allTransactions = new Vector<Transaction>();
    private KBarUnit kbarResult = null;

    public void streamingInput(String time, String value) {
        // build kbar unit
        // String[] input = line.split("\\s");
        // if(input.length != 3) {
        //     for(String s : input) {
        //         System.out.println(s);
        //     }
        //     throw new RuntimeException("Error input for building K bar...");
        // }

        if(kbarResult != null) {
            if(transactions.size() < ConfigurableParameters.MAX_CONCURRENT_TRANSACTION) {
                highTideStrategy(kbarResult, time, value);
            }
        }
        kbarBuilder.append(time, value);
        kbarResult = kbarBuilder.consumeAndMakeKBar();

    }

    private void highTideStrategy(KBarUnit kbarUnit, String time, String value) {
        Date date = null;
        try {
            date = formatter.parse(time);
        }
        catch(ParseException e) {
            e.printStackTrace();
        }
        double v = Double.parseDouble(value);
        if(kbarUnit.high - kbarUnit.low > 35 && v > kbarUnit.high) {
            Transaction trans = new Transaction(v, kbarUnit.endDate, Long.MAX_VALUE, 1, tolerance);
            allTransactions.add(trans);
            transactions.add(trans);
        }
        else if(kbarUnit.high - kbarUnit.low >= 35 && v < kbarUnit.low) {
            Transaction trans = new Transaction(v, kbarUnit.endDate, Long.MAX_VALUE, -1, tolerance);
            allTransactions.add(trans);
            transactions.add(trans);
            // stabilizing
        }
    }

    private int profit1 = 0;
    private int profit2 = 0;
    public void decideOffsetting(String newestTime, String newestValueStr) {
        double newestValue = Double.parseDouble(newestValueStr);
        Date newestDate = null;
        try {
            newestDate = formatter.parse(newestTime);
        }
        catch(ParseException e) {
            e.printStackTrace();
        }
        Vector<Transaction> transToRemove = new Vector<Transaction>();
        for(Transaction trans : transactions) {
            // if(newestDate.getTime() - trans.birthday.getTime() >= trans.lifecycle) {
            //     // Date oneMinuteLater = new Date(trans.birthday.getTime() + trans.lifecycle);
            //     profit0 += trans.offset(newestValue, newestDate);
            //     transToRemove.add(trans);
            //     System.out.println("Offsetted transaction: " + trans);
            //     System.out.println("Profit 0 = " + profit0);
            // }
            if( (newestValue-trans.price)*trans.prediction <= -tolerance) {
                profit1 += trans.offset(newestValue, newestDate);
                System.out.println("Offsetted transaction: " + trans);
                System.out.println("Profit 1 = " + profit1);
                transToRemove.add(trans);
            }
            else if( kbarBuilder.getLatestTrend() * trans.prediction == -1 ) {
                trans.b2bWrongPrediction++;
                if(trans.b2bWrongPrediction >= ConfigurableParameters.MAX_B2B_WRONG_PREDICTION) {
                    profit2 += trans.offset(newestValue, newestDate);
                    System.out.println("Offsetted transaction: " + trans);
                    System.out.println("Profit 2 = " + profit2);
                    transToRemove.add(trans);
                    // trans.b2bWrongPrediction = 0;
                }
            }
            else {
                // still earning within 1 min
                // reset wrong prediction
                trans.b2bWrongPrediction = 0;
            }
            // System.out.println(profit);
        }
        transactions.removeAll(transToRemove);
    }

    private int profit3 = 0;
    public void finishRemaining() {
        KBarUnit lastKBarUnit = kbarBuilder.getLastKbarUnit();
        if(lastKBarUnit == null) {
            return;
        }
        else {
            double newestPrice = lastKBarUnit.end;
            Date newestDate = lastKBarUnit.endDate;
            for(Transaction trans : transactions) {
                profit3 += trans.offset(newestPrice, newestDate);
            }
        }
    }

    public void logfileTest(String... args) {
        // input = k bar result
        BufferedReader reader = null;
        DataBroadcaster broadcaster = DataBroadcaster.getInstance();
        try {
            String line;
            reader = new BufferedReader(new FileReader(args[0]));
            // System.out.println("Input...");
            while((line=reader.readLine()) != null) {
                if(line.startsWith("#") || line.trim().equals("")) {
                    continue;
                }
                String[] input = line.split("\\s");
                if(input.length < 3) {
                    for(String s : input) {
                        System.out.println(s);
                    }
                    throw new RuntimeException("Error input for building K bar...");
                }
                streamingInput(input[1], input[2]);
                decideOffsetting(input[1], input[2]);
                // System.out.println(line);
            }
            reader.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        // System.out.println(bbandBuilder);
    }


    public void onlineTest() {
        long timeShifting = 0;
        Date deadline = null;
        Date today = new Date();
        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat yyyyMMddHHmmss = new SimpleDateFormat("yyyyMMddHHmmss");
        SimpleDateFormat timeStampForKBar = new SimpleDateFormat("yyyyMMdd HHmmss");
        try {
            String datePrefix = yyyyMMdd.format(today);
            deadline = yyyyMMddHHmmss.parse(datePrefix + ConfigurableParameters.TRANSACTION_DEADLINE);
            System.out.println("Deadline of Transaction: " + deadline);
        }
        catch(ParseException e) {
            e.printStackTrace();
        }
        try {
            while(true) {
                long t1 = System.currentTimeMillis();
                double price = -1;
                if(ConfigurableParameters.COMMODITY.contains("MX")) {
                    price = RealTimePrice.getMTXPrice();
                }
                else if(ConfigurableParameters.COMMODITY.contains("TX")) {
                    price = RealTimePrice.getTXPrice();
                }
                timeShifting = System.currentTimeMillis() - t1;
                if(timeShifting > ConfigurableParameters.REALTIME_PRICE_REFRESH_RATE) {
                    // this request may take too much time. Let's ignore it.
                    continue;
                }
                if(price != -1) {
                    String timeStamp = timeStampForKBar.format(Calendar.getInstance().getTime());
                    String line = timeStamp + " " + price;
                    System.out.println("# " + line);
                    String[] input = line.split("\\s");
                    if(input.length != 3) {
                        for(String s : input) {
                            System.out.println(s);
                        }
                        throw new RuntimeException("Error input for building K bar...");
                    }
                    streamingInput(input[1], input[2]);
                    decideOffsetting(input[1], input[2]);
                }
                if(timeShifting < ConfigurableParameters.REALTIME_PRICE_REFRESH_RATE) {
                    try {
                        Thread.sleep(ConfigurableParameters.REALTIME_PRICE_REFRESH_RATE - timeShifting);
                    }
                    catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Date now = new Date();
                if(now.after(deadline)) {
                    break;
                }
            }
        }
        finally {
            saveResults();
        }
    }

    public String toString() {
        String ret = "# Transactions:\n";
        for(Transaction trans : allTransactions) {
            String line = trans.toString();
            if(line.equals("")) {
                continue;
            }
            else {
                ret += line + "\n";
            }
        }
        ret += "# Total number of transactions = " + allTransactions.size() + "\n";
        ret += "# Profit 1: Stop losing.\n";
        ret += "# Profit 2: Reach max wrong prediction limit\n";
        ret += "# Profit 3: Remaining transactions.\n";
        ret += "# -------------------------------------------------------\n";
        ret += "# Profit 1 = " + profit1 + "\n";
        ret += "# Profit 2 = " + profit2 + "\n";
        ret += "# Profit 3 = " + profit3 + "\n";
        return ret += "# Final profit = " + (profit1 + profit2 + profit3);
    }

    // private void saveAsJpeg(File outFile) throws IOException {
    //     final XYSeriesCollection data = new XYSeriesCollection();
    //
    //     XYSeries priceSeries = new XYSeries("Price");
    //     XYSeries upperSeries = new XYSeries("Upper");
    //     XYSeries lowerSeries = new XYSeries("Lower");
    //     double max = Double.MIN_VALUE;
    //     double min = Double.MAX_VALUE;
    //     long initTime = bbandBuilder.getBBandSequence().get(0).dateStart.getTime();
    //     for(BBandUnit bbandUnit : bbandBuilder.getBBandSequence()) {
    //         double upper = bbandUnit.upperBound;
    //         double lower = bbandUnit.lowerBound;
    //         if(upper == Double.MAX_VALUE || lower == Double.MIN_VALUE) {
    //             // initializing part
    //             continue;
    //         }
    //         long t1 = (bbandUnit.dateStart.getTime() - initTime)/1000;
    //         long t2 = (bbandUnit.dateEnd.getTime() - initTime)/1000;
    //
    //         // priceSeries.add(t1, bbandUnit.start);
    //         priceSeries.add(t2, bbandUnit.end);
    //
    //         if(upper != 0) {
    //             data.addSeries(upperSeries);
    //             // upperSeries.add(t1, upper);
    //             upperSeries.add(t2, upper);
    //
    //             if(upper > max) max = upper;
    //             if(upper < min) min = upper;
    //         }
    //         if(lower != 0) {
    //
    //             // lowerSeries.add(t1, lower);
    //             lowerSeries.add(t2, lower);
    //             data.addSeries(lowerSeries);
    //
    //             if(lower > max) max = lower;
    //             if(lower < min) min = lower;
    //         }
    //
    //     }
    //
    //     data.addSeries(priceSeries);
    //
    //     for(Transaction trans : allTransactions) {
    //         XYSeries transSeries = new XYSeries(formatter.format(trans.birthday));
    //         long t1 = (trans.birthday.getTime() - initTime)/1000;
    //         long t2 = (trans.dateOffset.getTime() - initTime)/1000;
    //         transSeries.add(t1, trans.price);
    //         transSeries.add(t2, trans.offsetValue);
    //         data.addSeries(transSeries);
    //     }
    //
    //     final JFreeChart chart = ChartFactory.createXYLineChart("HighTide", "Time", "Point", data,
    //         PlotOrientation.VERTICAL, false, true, false);
    //     chart.setAntiAlias(false);
    //     XYPlot plot = (XYPlot) chart.getXYPlot();
    //     plot.setBackgroundPaint(java.awt.Color.BLACK);
    //     int seriesCount = plot.getSeriesCount();
    //     for (int i = 0; i < seriesCount; i++) {
    //         plot.getRenderer().setSeriesStroke(i, new BasicStroke(3));
    //     }
    //     final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
    //     rangeAxis.setRange(min-20, max+20);
    //     rangeAxis.setTickUnit(new NumberTickUnit(10));
    //     final NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
    //     domainAxis.setTickUnit(new NumberTickUnit(60));
    //     domainAxis.setRange(0, 17940);
    //     domainAxis.setVerticalTickLabels(true);
    //     int width = 1280*10; /* Width of the image */
    //     int height = 720; /* Height of the image */
    //     ChartUtilities.saveChartAsJPEG(outFile, 1.0f, chart, width, height);
    // }

    private void saveResults() {
        finishRemaining();
        System.out.println(this);
        // for network streaming input test
        // String line = getNetworkInput();
        // streamingInput(line);
        Date today = new Date();
        String filename = formatter.format(today);
        // Write out transaction data points
        try {
            File outFile = new File("output/transaction/" + filename);
            PrintWriter pw = new PrintWriter(new FileWriter(outFile));
            pw.println(this);
            pw.close();
        }
        catch(IOException e) {
            e.printStackTrace();
        }

    }


    public static void main(String... args) {
        HighTide ht = new HighTide();
        String ret1 = T4.addAccCA();
        String ret2 = T4.verifyCAPass();
        System.out.println(ret1);
        System.out.println(ret2);
        ht.onlineTest();
    }

}
