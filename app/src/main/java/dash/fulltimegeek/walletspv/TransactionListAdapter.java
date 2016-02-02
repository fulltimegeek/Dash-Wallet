package dash.fulltimegeek.walletspv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.MonetaryFormat;
import org.spongycastle.util.encoders.Hex;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by fulltimegeek on 1/31/16.
 */
public class TransactionListAdapter extends ArrayAdapter<Transaction> {
    List<Transaction> objects;
    DashGui context;
    int viewResourceId;

    public TransactionListAdapter(Context context, int viewResourceId,
                            List<Transaction> objects) {
        super(context, viewResourceId, objects);
        this.context = (DashGui)context;
        this.viewResourceId = viewResourceId;
        this.objects = objects;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        Transaction tx = objects.get(position);
        if(row == null){
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(viewResourceId, parent, false);
        }
        if((position+1)%2 == 0) {
            row.setBackgroundColor(Color.parseColor("#ffdddddd"));
        }else{
            row.setBackgroundColor(Color.WHITE);
        }
        TextView tvAddress = (TextView) row.findViewById(R.id.tv_history_row_address);
        TextView tvAmount = (TextView) row.findViewById(R.id.tv_history_row_amount);
        TextView tvTime = (TextView) row.findViewById(R.id.tv_history_row_time);
        TextView tvConf = (TextView) row.findViewById(R.id.tv_history_row_conf);


        Address address =  null;
        Coin coinToMe = tx.getValueSentToMe(context.service.kit.wallet());
        Coin coinFromMe = tx.getValueSentFromMe(context.service.kit.wallet());
        String value = MonetaryFormat.BTC.format(coinToMe.subtract(coinFromMe)).toString().replace(MonetaryFormat.CODE_BTC,"").trim();
        Date date = tx.getUpdateTime();
        DateFormat formatter = new SimpleDateFormat("MM/dd/yy HH:mm");
        if(coinToMe.isGreaterThan(coinFromMe)){
            value = "+"+value;
            address =  tx.getOutput(0).getScriptPubKey().getToAddress(MainNetParams.get());
            tvAmount.setTextColor(Color.BLACK);
        }else{
            for(TransactionOutput out : tx.getOutputs()){
               if(!out.isMine(context.service.kit.wallet())){
                   address =  out.getScriptPubKey().getToAddress(MainNetParams.get());
               }
            }
            tvAmount.setTextColor(Color.RED);
        }
        try {
            String time = formatter.format(date);
            tvTime.setText(time);
        }catch(Exception e){
            e.printStackTrace();
        }
        if(address != null) {
            //tvAddress.setText(address.toString().substring(0, 11) + "\n" +
            //        address.toString().substring(11, 24) + "\n" +
            //        address.toString().substring(24, 34));
            tvAddress.setText(address.toString());
            tvAmount.setText(value);
        }
        String confirmations = tx.getConfidence().getDepthInBlocks()>=6?"C":tx.getConfidence().getDepthInBlocks()+ "C";
        tvConf.setText(confirmations);
        return row;

    }
}
