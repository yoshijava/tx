package oracle.common;
import java.util.*;
import java.text.*;
import oracle.sinopac.*;

public class Transaction {
    public double price;
    public Date birthday;
    public Date dateOffset;
    public long lifecycle;
    public int prediction;
    public double tolerance;
    public double offsetValue;
    public int earning;
    public int b2bWrongPrediction;
    public int taxfee = ConfigurableParameters.TAX_FEE;
    public int ntdPerPoint = ConfigurableParameters.NTD_PER_POINT;
    private SimpleDateFormat formatter = new SimpleDateFormat("HHmmss");
    private FutureStruct futureStruct;

    public Transaction(double price, Date birthday, long lifecycle, int prediction, double tolerance) {
        this.price = price;
        this.birthday = birthday;
        this.lifecycle = lifecycle;
        this.prediction = prediction;
        this.tolerance = tolerance;
    }

    public int offset(double newestValue, Date dateOffset) {
        this.dateOffset = dateOffset;
        this.offsetValue = newestValue;
        boolean offseted = false;
        boolean successMadeOffsetTicket = false;
        // make offset ticket
        while(successMadeOffsetTicket != true) {
            if(prediction == -1) {
                successMadeOffsetTicket = T4.makeOffsetMTXFutureTicket("B", "" + (int) (newestValue - 100), "1");
            }
            else if(prediction == 1) {
                successMadeOffsetTicket = T4.makeOffsetMTXFutureTicket("S", "" + (int) (newestValue + 100), "1");
            }
            if(successMadeOffsetTicket == false) {
                try {
                    Thread.sleep(100); // wait for 1 sec and then try to buy ticket again
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        // TODO
        // check if offset succeed
        while(offseted != true) {

        }
        earning = ((int)((newestValue-price)*prediction))*ntdPerPoint - taxfee;
        return earning;
    }

    public boolean order() {
        for(int i=0; i<ConfigurableParameters.NUM_TRY_TO_ORDER; i++) {
            FutureStruct future = null;
            if(prediction == -1) {
                future = T4.makeMTXFutureTicket("S", "" + (int) price, "1");
            }
            else if(prediction == 1) {
                future = T4.makeMTXFutureTicket("B", "" + (int) price, "1");
            }
            if(future != null) {
                return true;
            }
            else {
                try {
                    Thread.sleep(1000); // wait for 1 sec and then try to buy ticket again
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public String toString() {
        if(prediction!=0) {
            return formatter.format(birthday) + " " + formatter.format(dateOffset) + " prediction=" + prediction + " " +
                price + "->" + offsetValue + " earning=" + earning;
        }
        else {
            return "";
        }
    }
}
