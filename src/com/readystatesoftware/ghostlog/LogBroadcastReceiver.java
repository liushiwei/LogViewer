package com.readystatesoftware.ghostlog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class LogBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())){
			SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			if(mPrefs.getBoolean(context.getString(R.string.log_auto_save), false)){
				context.startService(new Intent(context, LogSaveService.class));
			};
		}
		if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
			Uri uri = intent.getData();
			 String path = uri.getPath();
	         Log.e("LogViewer", "----------MOUNT: "+path.substring(path.lastIndexOf('/')));
	         SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	         String pathString = mPrefs.getString(context.getString(R.string.pref_log_path), "");
	         Log.e("LogViewer", "----------save path: "+pathString.substring(pathString.lastIndexOf('/')));
	         if(!pathString.substring(pathString.lastIndexOf('/')).equals(path.substring(path.lastIndexOf('/')))){
	        	 return;
	         }
	         if(mPrefs.getBoolean(context.getString(R.string.pref_log_auto_save), false)){
	        		 context.startService(new Intent(context, LogSaveService.class));
	         };
			
		}
		if (intent.getAction().equals(Intent.ACTION_MEDIA_EJECT)) {
			Uri uri = intent.getData();
			 String path = uri.getPath();
	         Log.e("LogViewer", "----------EJECT: "+path);
	         SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	         String pathString = mPrefs.getString(context.getString(R.string.pref_log_path), "");
	         if(!pathString.substring(pathString.lastIndexOf('/')).equals(path.substring(path.lastIndexOf('/')))){
	        	 return;
	         }
	         context.stopService(new Intent(context, LogSaveService.class));
		}

	}

}
