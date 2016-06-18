package com.readystatesoftware.ghostlog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class LogBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())){
			SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			if(mPrefs.getBoolean(context.getString(R.string.log_auto_save), false)){
				context.startService(new Intent(context, LogSaveService.class));
			};
		}

	}

}
