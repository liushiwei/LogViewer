package com.readystatesoftware.ghostlog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.nolanlawson.logcat.helper.LogcatHelper;
import com.nolanlawson.logcat.helper.RuntimeHelper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

public class LogSaveService extends Service {

	@Override
	public IBinder onBind(Intent intent) {

		return null;
	}

	LogReaderAsyncTask mLogReaderAsyncTask;
	String mSaveFile;

	@Override
	public void onCreate() {

		SharedPreferences mPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		// 创建一个通知
		Notification mNotification = new Notification();

		// 设置属性值
		mNotification.icon = R.drawable.ic_launcher;
		mNotification.tickerText = "Log Saving";
		mNotification.when = System.currentTimeMillis(); // 立即发生此通知

		// 带参数的构造函数,属性值如上
		// Notification mNotification = = new
		// Notification(R.drawable.icon,"NotificationTest",
		// System.currentTimeMillis()));

		// 添加声音效果
		mNotification.defaults |= Notification.DEFAULT_SOUND;

		// 添加震动,后来得知需要添加震动权限 : Virbate Permission
		// mNotification.defaults |= Notification.DEFAULT_VIBRATE ;

		// 添加状态标志

		// FLAG_AUTO_CANCEL 该通知能被状态栏的清除按钮给清除掉
		// FLAG_NO_CLEAR 该通知能被状态栏的清除按钮给清除掉
		// FLAG_ONGOING_EVENT 通知放置在正在运行
		// FLAG_INSISTENT 通知的音乐效果一直播放
		mNotification.flags = Notification.FLAG_ONGOING_EVENT;

		// 将该通知显示为默认View
		Time time = new Time();
		String pathString = mPrefs.getString(getString(R.string.pref_log_path),
				"/mnt/sdcard");
		DateFormat df5 = new SimpleDateFormat("yyyy-MM-dd-hh-mm", Locale.US);

		mSaveFile = pathString + "/log_" + df5.format(new Date()) + ".txt";
		mNotification.setLatestEventInfo(this, "Log Saving", "Save to "
				+ mSaveFile, null);

		// 设置setLatestEventInfo方法,如果不设置会App报错异常
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		// 注册此通知
		// 如果该NOTIFICATION_ID的通知已存在，会显示最新通知的相关信息 ，比如tickerText 等
		mNotificationManager.notify(2, mNotification);
		Log.e("TAG", "START Service show Notification");
		mLogReaderAsyncTask = new LogReaderAsyncTask();
		mLogReaderAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

	}

	private void stopLogReader() {
		if (mLogReaderAsyncTask != null) {
			mLogReaderAsyncTask.cancel(true);
		}
		mLogReaderAsyncTask = null;
		Log.i("LogSaveService", "Log reader task stopped");
	}

	@Override
	public void onDestroy() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		// 取消的只是当前Context的Notification
		mNotificationManager.cancel(2);
		stopLogReader();
		super.onDestroy();
	}

	public static boolean getSaveLogToFile(String buffer, String fileName) {
		Process dumpLogcatProcess = null;
		BufferedReader reader = null;
		String result = null;
		try {

			List<String> args = LogcatHelper.getLogcatArgs(buffer);
			args.add("-d"); // -d just dumps the whole thing

			dumpLogcatProcess = RuntimeHelper.exec(args);
			reader = new BufferedReader(new InputStreamReader(
					dumpLogcatProcess.getInputStream()), 8192);
			File outPutFile = new File(fileName);
			if (!outPutFile.exists()) {
				if (!outPutFile.createNewFile())
					return false;
			}
			FileOutputStream outputStream = new FileOutputStream(outPutFile);

			String line;
			while ((line = reader.readLine()) != null) {
				outputStream.write(line.getBytes());
				outputStream.write('\n');
			}
			outputStream.close();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (dumpLogcatProcess != null) {
				RuntimeHelper.destroy(dumpLogcatProcess);
				Log.d("LogSaveService", "destroyed 1 dump logcat process");
			}
			// post-jellybean, we just kill the process, so there's no need
			// to close the bufferedReader. Anyway, it just hangs.
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
					&& reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return false;

	}

	class LogReaderAsyncTask extends AsyncTask<Integer, LogLine, Boolean> {
		Process process = null;
		BufferedReader reader = null;
		boolean ok = true;

		@Override
		protected Boolean doInBackground(Integer... params) {
			try {

				process = LogcatHelper.getLogcatProcess("main");
				reader = new BufferedReader(new InputStreamReader(
						process.getInputStream()), 8192);
				File outPutFile = new File(mSaveFile);
				if (!outPutFile.exists()) {
					if (!outPutFile.createNewFile())
						return false;
				}
				FileOutputStream outputStream = new FileOutputStream(outPutFile);

				String line;
				while (!isCancelled() && (line = reader.readLine()) != null) {
					outputStream.write(line.getBytes());
					outputStream.write('\n');
				}
				outputStream.close();

			} catch (IOException e) {
				
				e.printStackTrace();
				ok = false;

			} catch (Exception e) {
				e.printStackTrace();
				ok = false;

			} finally {

				if (process != null) {
					RuntimeHelper.destroy(process);
				}

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
						&& reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			}
			return null;
		}

	}

}
