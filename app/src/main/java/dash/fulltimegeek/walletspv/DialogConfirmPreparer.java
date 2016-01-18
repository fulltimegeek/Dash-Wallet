package dash.fulltimegeek.walletspv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DialogConfirmPreparer {
	private TextView tvTitle;
	Activity context;
	public AlertDialog alert;
	DialogInterface.OnClickListener listener;
	String title;
	String negative;
	String positive;
	boolean cancelable;
	
	final public static int CANCEL = -1;
	final public static int OK = -2;
	
	
	public DialogConfirmPreparer(Activity context, DialogInterface.OnClickListener listener, String title, String negative, String positive, boolean cancelable){
		this.context = context;
		this.listener = listener;
		this.title = title;
		this.negative = negative;
		this.positive = positive;
		this.cancelable = cancelable;
		create();
	}


    private void create(){
    	LayoutInflater inflater = context.getLayoutInflater();
       alert =  new AlertDialog.Builder(context).setCancelable(cancelable)
                .setNegativeButton(android.R.string.ok, listener)
                .setPositiveButton(
                        android.R.string.cancel, listener)
                .create();
		View layout = (LinearLayout) inflater.inflate(
				R.layout.layout_dialog, null);
		tvTitle = (TextView) layout.findViewById(R.id.tv_dialog);
		tvTitle.setText(title);
		alert.setView(layout, 0, 0, 0, 0);
		alert.setOnShowListener(new OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				// ((AlertDialog)dialog).getButton(AlertDialog.BUTTON_POSITIVE).setFocusable(true);
				finishDialog();
			}
		});
		alert.setOnShowListener(new OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				finishDialog();
			}
		});
	}

	public void finishDialog() {
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
		params.weight = 1;
		int dip = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				5, context.getResources().getDisplayMetrics());
		int sp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
				12, context.getResources().getDisplayMetrics());
		params.setMargins(dip, dip, dip, dip);
		if (context.getResources() == null) {
			Log.e("Resources", "Resources NULL");
		} else if (context.getResources().getDrawable(R.drawable.btn_dialog_positive) == null) {
			Log.e("Resources", "Drawable NULL");
		} else if (alert.getButton(AlertDialog.BUTTON_NEGATIVE) == null) {
			Log.e("Resources", "Button NULL");
		}
		final Button positive = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
		final Button negative = alert.getButton(AlertDialog.BUTTON_POSITIVE);
		negative.setBackgroundDrawable(context.getResources().getDrawable(
				R.drawable.btn_dialog_negative));
		negative.setLayoutParams(params);
		negative.setTextColor(Color.WHITE);
		negative.setText(this.negative);
		negative.setTypeface(null,Typeface.BOLD);
		negative.setTextSize(sp);
		negative.setOnTouchListener(new OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					negative.setBackgroundDrawable(context.getResources().getDrawable(
							R.drawable.btn_dialog_pressed));
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					negative.setBackgroundDrawable(context.getResources().getDrawable(
							R.drawable.btn_dialog_negative));
				}
				return false;
			}});
		positive.setBackgroundDrawable(context.getResources().getDrawable(
				R.drawable.btn_dialog_positive));
		positive.setLayoutParams(params);
		positive.setTextSize(sp);
		positive.setText(this.positive);
		positive.setTypeface(null,Typeface.BOLD);
		positive.setTextColor(Color.WHITE);
		positive.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					positive.setBackgroundDrawable(context.getResources().getDrawable(
							R.drawable.btn_dialog_pressed));
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					positive.setBackgroundDrawable(context.getResources().getDrawable(
							R.drawable.btn_dialog_positive));
				}
				return false;
			}
		});
	}
	
	public void setTitle(String title){
		this.title = title;
		tvTitle.setText(title);
	}
}