package br.com.thinkti.android.filechooser;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;


import dash.fulltimegeek.walletspv.R;

public class AdvFileChooser extends Activity {
	private File currentDir;
	private FileArrayAdapter adapter;
	private FileFilter fileFilter;
	private File fileSelected;
	private ArrayList<String> extensions;
	private boolean selectFolder = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_view);
		

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.adMob);

		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (extras.getStringArrayList("filterFileExtension") != null) {
				extensions = extras.getStringArrayList("filterFileExtension");				
				fileFilter = new FileFilter() {
					@Override
					public boolean accept(File pathname) {						
						return ((pathname.isDirectory()) || (pathname.getName().contains(".")?extensions.contains(pathname.getName().substring(pathname.getName().lastIndexOf("."))):false));
					}
				};
			}
			if (extras.getBoolean("selectFolder")) {				
				selectFolder = true;
				fileFilter = new FileFilter() {
					@Override
					public boolean accept(File pathname) {						
						return (pathname.isDirectory());
					}
				};									
			}
		}
		
		currentDir = new File("/sdcard/");
		fill(currentDir);		
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	if ((!currentDir.getName().equals("sdcard")) && (currentDir.getParentFile() != null)) {
	        	currentDir = currentDir.getParentFile();
	        	fill(currentDir);
        	} else {
        		finish();
        	}
            return false;
        }
        return super.onKeyDown(keyCode, event);
	}

	private void fill(File f) {
		File[] dirs = null;
		if (fileFilter != null)
			dirs = f.listFiles(fileFilter);
		else 
			dirs = f.listFiles();
			
		this.setTitle(getString(R.string.currentDir) + ": " + f.getName());
		List<Option> dir = new ArrayList<Option>();
		List<Option> fls = new ArrayList<Option>();
		try {
			for (File ff : dirs) {
				if (ff.isDirectory() && !ff.isHidden())
					dir.add(new Option(ff.getName(), getString(R.string.folder), ff
							.getAbsolutePath(), true, false, false));
				else {
					if (!ff.isHidden())
						fls.add(new Option(ff.getName(), getString(R.string.fileSize) + ": "
								+ ff.length(), ff.getAbsolutePath(), false, false, false));
				}
			}
		} catch (Exception e) {

		}
		Collections.sort(dir);
		Collections.sort(fls);
		dir.addAll(fls);
		if (!f.getName().equalsIgnoreCase("sdcard")) {
			if (f.getParentFile() != null) dir.add(0, new Option("..", getString(R.string.parentDirectory), f.getParent(), false, true, true));
		}
		
		ListView listView = (ListView) findViewById(R.id.lvFiles);
		
		adapter = new FileArrayAdapter(listView.getContext(), R.layout.file_view,
				dir);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> l, View v, int position,
					long id) {
				// TODO Auto-generated method stub
				Option o = adapter.getItem(position);
				if (!o.isBack())
					doSelect(o);
				else {
					currentDir = new File(o.getPath());
					fill(currentDir);
				}	
			}
			
		});
	}
	
	private void doSelect(final Option o) {
		if (o.isFolder() || o.isParent()) {
			if (!selectFolder) {
				currentDir = new File(o.getPath());
				fill(currentDir);
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getString(R.string.optionSelection))
				       .setCancelable(false)
				       .setPositiveButton(getString(R.string.openFolder), new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        	   currentDir = new File(o.getPath());
				        	   fill(currentDir);
				           }
				       })
				       .setNegativeButton(getString(R.string.selectThis), new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        	   	fileSelected = new File(o.getPath());
								Intent intent = new Intent();
								intent.putExtra("fileSelected", fileSelected.getAbsolutePath());
								setResult(Activity.RESULT_OK, intent);
								finish();
				           }
				       });
				AlertDialog alert = builder.create();
				alert.show();
			}
		} else {
			//onFileClick(o);
			fileSelected = new File(o.getPath());
			Intent intent = new Intent();
			intent.putExtra("fileSelected", fileSelected.getAbsolutePath());
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
}
