package com.readystatesoftware.ghostlog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import android.os.AsyncTask;

public class DeviceInfoAsyncTask extends AsyncTask<Void, DeviceInfo, Boolean> {

	@Override
	protected Boolean doInBackground(Void... params) {
		while (!isCancelled()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			int source = getSource();
			publishProgress(new DeviceInfo(source));
		}
			
		return false;
	}
	
	
	private int getSource() {
		return getValue("/sys/class/mub/source/index");
	}

	private int getValue(String fileName) {
		int value = 0;
		File file = new File(fileName);
		if (file.exists()) {
			BufferedReader buf;
			String source = null;
			try {
				buf = new BufferedReader(new FileReader(file));

				source = buf.readLine();
				if (source != null) {
					value = Integer.valueOf(source);
				} else {
					value = 256;
					file.delete();
				}
				buf.close();

			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return value;
	}
}
