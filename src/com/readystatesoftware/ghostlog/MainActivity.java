/*
 * Copyright (C) 2013 readyState Software Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.readystatesoftware.ghostlog;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.nolanlawson.logcat.helper.LogcatHelper;


public class MainActivity extends BasePreferenceActivity {

    private static final int CODE_TAG_FILTER = 1;
    private static Preference sTagFilterPref;
    
    private static int mDeviceId;

    private SharedPreferences mPrefs;

    private BroadcastReceiver mRootFailReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            processRootFail();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View actionBarView = inflater.inflate(R.layout.action_bar, null);
        Switch mainSwitch = (Switch) actionBarView.findViewById(R.id.switch1);
        mainSwitch.setChecked(LogService.isRunning());
        mainSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Intent intent = new Intent(MainActivity.this, LogService.class);
                intent.putExtra("device_id", mDeviceId);
                if (b) {
                    if (!LogService.isRunning()) {
                        startService(intent);
                        getActionBar().getCustomView().findViewById(R.id.spinner1).setEnabled(false);
                    }
                } else {
                    stopService(intent);
                    getActionBar().getCustomView().findViewById(R.id.spinner1).setEnabled(true);
                }
            }
        });
        Spinner spinner = (Spinner) actionBarView.findViewById(R.id.spinner1);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				mDeviceId = position;
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// TODO Auto-generated method stub
				
			}
        	
		});

        final ActionBar bar = getActionBar();
        final ActionBar.LayoutParams lp = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.rightMargin = getResources().getDimensionPixelSize(R.dimen.main_switch_margin_right);
        bar.setCustomView(actionBarView, lp);
        bar.setDisplayShowCustomEnabled(true);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!mPrefs.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
            PreferenceManager.setDefaultValues(this, R.xml.pref_filters, true);
            PreferenceManager.setDefaultValues(this, R.xml.pref_appearance, true);
            SharedPreferences.Editor edit = mPrefs.edit();
            edit.putBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, true);
            edit.apply();
        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        setupSimplePreferencesScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(LogService.ACTION_ROOT_FAILED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mRootFailReceiver, f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRootFailReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_TAG_FILTER) {
            if (resultCode == RESULT_OK) {
                mPrefs.edit().putString(getString(R.string.pref_tag_filter), data.getAction()).apply();
                sTagFilterPref.setSummary(data.getAction());
            }
        }
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sTagFilterPref = null;
    }
    String mLogPath;
    private AlertDialog mDialog;
    Handler mHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			if(msg.what==0){
				Toast.makeText(getApplication(), "Save Success!", Toast.LENGTH_SHORT).show();
				mDialog.dismiss();
			}
			super.handleMessage(msg);
		}
    	
    };

    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    @SuppressWarnings("deprecation")
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        addPreferencesFromResource(R.xml.pref_blank);

        // Add 'filters' preferences.
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.log_save);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_save);
        final ListPreference preference = (ListPreference) findPreference(getString(R.string.pref_log_path));
        if(preference.getValue()!=null){
        	
        	mLogPath=preference.getValue();
        	mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					preference.setSummary(mLogPath);
					
				}
			});
        	
        	
        }
        String[] path = getPaths();
        preference.setEntries(path);
        preference.setEntryValues(path);
        
        preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mLogPath=newValue.toString();
				preference.setSummary(mLogPath);
				Log.e("TAG", "------"+newValue.toString());
				
				return true;
			}
		});
        Preference saveNowPreference  = findPreference(getString(R.string.pref_log_save_now));
        saveNowPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			

			@Override
			public boolean onPreferenceClick(Preference preference) {
				if(mLogPath!=null){
					final DateFormat df5 = new SimpleDateFormat("yyyy-MM-dd-hh-mm", Locale.US); 
				    
					mDialog = new AlertDialog.Builder(MainActivity.this).setTitle(R.string.log_save_now).setMessage("Save log file to "+mLogPath+"/log_"+df5.format(new Date())+".txt").create();
					mDialog.show();
					new Thread(){

						@Override
						public void run() {
							LogcatHelper.getSaveLogToFile("main", mLogPath+"/log_"+df5.format(new Date())+".txt");
							mHandler.sendEmptyMessage(0);
							super.run();
						}
						
					}.start();
				}else {
					Toast.makeText(getBaseContext(), "Set Log File Path First!", Toast.LENGTH_SHORT).show();
				}
				return false;
			}
		});
        
        SwitchPreference startSavePreference  = (SwitchPreference) findPreference(getString(R.string.pref_log_start_save));
        startSavePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if(!checkSavePath(mLogPath)){
					Toast.makeText(MainActivity.this, "请设置正确的日志路径", Toast.LENGTH_SHORT).show();
					return false;
				}
				SwitchPreference startSavePreference = (SwitchPreference) preference;
				if(!startSavePreference.isChecked()){
					startService(new Intent(MainActivity.this,LogSaveService.class));
				}else{
					stopService(new Intent(MainActivity.this,LogSaveService.class));
				}
				return true;
			}
		});
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.filters);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_filters);
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_log_level)));
        setupTagFilterPreference(this, findPreference(getString(R.string.pref_tag_filter)));
        sTagFilterPref = findPreference(getString(R.string.pref_tag_filter));

        // Add 'appearance' preferences.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.appearance);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_appearance);

        // Add 'info' preferences.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.information);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_info);
        setupOpenSourceInfoPreference(this, findPreference(getString(R.string.pref_info_open_source)));
        setupVersionPref(this, findPreference(getString(R.string.pref_version)));

    }
    
    private boolean checkSavePath(String path){
    	if(path==null)
    		return false;
    	File file = new File(path);
    	return file.canWrite();
    }

    private String[] getPaths() {
		File name = new File("/mnt/");
		ArrayList<String> paths = new ArrayList<String>();
		for(File path:name.listFiles(new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				return pathname.canWrite();
			}
		})){
			Log.e("TAG", path.getAbsolutePath());
			paths.add(path.getAbsolutePath());
		}
		String[] returnString = new String[paths.size()];  ;
		paths.toArray(returnString);
		return returnString;
	}

	private void processRootFail() {

        int failCount = mPrefs.getInt(getString(R.string.pref_root_fail_count), 0);
        if (failCount == 0) {
            // show dialog first time
            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_root)
                    .setMessage(R.string.no_root_dialog)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // ok, do nothing
                        }
                    })
                    .setNeutralButton(getString(R.string.github), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.repo_url)));
                            startActivity(intent);
                        }
                    })
                    .create();
            dlg.show();
            mPrefs.edit().putInt(getString(R.string.pref_root_fail_count), failCount+1).apply();
        } else if (failCount <= 3) {
            // show toast 3 more times
            Toast.makeText(this, R.string.toast_no_root, Toast.LENGTH_LONG).show();
            mPrefs.edit().putInt(getString(R.string.pref_root_fail_count), failCount+1).apply();
        }

    }

    private static void setupTagFilterPreference(final Activity activity, Preference preference) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        preference.setSummary(prefs.getString(activity.getString(R.string.pref_tag_filter), null));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(activity, TagFilterListActivity.class);
                activity.startActivityForResult(intent, CODE_TAG_FILTER);
                return true;
            }
        });
    }

    private static void setupOpenSourceInfoPreference(final Activity activity, Preference preference) {
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager fm = activity.getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                Fragment prev = fm.findFragmentByTag("dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);
                new OpenSourceLicensesDialog().show(ft, "dialog");
                return true;
            }
        });
    }

    private static void setupVersionPref(final Activity activity, Preference preference) {
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FragmentManager fm = activity.getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                Fragment prev = fm.findFragmentByTag("dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);
                new AboutDialog().show(ft, "dialog");
                return true;
            }
        });
    }

    public static class FilterPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_filters);
            bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_log_level)));
            setupTagFilterPreference(getActivity(), findPreference(getString(R.string.pref_tag_filter)));
            sTagFilterPref = findPreference(getString(R.string.pref_tag_filter));
        }
    }

    public static class AppearancePreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_appearance);
        }
    }

    public static class InfoPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.pref_info);
            setupOpenSourceInfoPreference(getActivity(), findPreference(getString(R.string.pref_info_open_source)));
            setupVersionPref(getActivity(), findPreference(getString(R.string.pref_version)));
        }
    }

    public static class OpenSourceLicensesDialog extends DialogFragment {

        public OpenSourceLicensesDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            WebView webView = new WebView(getActivity());
            webView.loadUrl("file:///android_asset/licenses.html");

            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.open_source_licences)
                    .setView(webView)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }
    }

    public static class AboutDialog extends DialogFragment {

        public AboutDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View content = inflater.inflate(R.layout.dialog_about, null, false);
            TextView version = (TextView) content.findViewById(R.id.version);

            try {
                String name = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
                version.setText(getString(R.string.version) + " " + name);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.app_name)
                    .setView(content)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }
    }

}
