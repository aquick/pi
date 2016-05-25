/*****************************************************************************
 *   Copyright 2016 Andy Quick
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *****************************************************************************/
package org.gmplib.test.pi;

import android.app.Activity;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import org.gmplib.gmpjni.GMP;
import org.gmplib.gmpjni.GMP.mpf_t;
import org.gmplib.gmpjni.GMP.GMPException;

public class MainActivity extends Activity implements UI {

    private TextView mView;
    private TextView mDigits;
    private Button mButton;
    AsyncTask<Integer, Integer, Integer> task = null;
    private mpf_t refPi;
    private String refPiStr;
    private static final double BITS_PER_DIGIT  =  3.32192809488736234787;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);
        mView = (TextView) findViewById(R.id.TextView01);
        mDigits = (TextView) findViewById(R.id.TextView02);
        mButton = (Button) findViewById(R.id.Button01);
        mButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v)
                    {
                	try {
                	    MainActivity.this.mView.setText("");
                	    int d = 0;
                            StringBuffer sb = new StringBuffer();
                            sb.append(MainActivity.this.mDigits.getText());
                            try {
                	        d = Integer.parseInt(sb.toString());
                            }
                            catch (NumberFormatException e) {
                            }
                            task = new PI_Task(MainActivity.this);
                            if (d == 0) {
                                task.execute();
                            } else {
                        	if (d >= MainActivity.this.refPiStr.length()) {
                    	            MainActivity.this.display(
                    	                MainActivity.this.getResources().getString(R.string.warning1));
                        	}
                        	task.execute(Integer.valueOf(d));
                            }
                	}
                	catch (GMPException e) {
                	    Log.d("PI_Task", "MainActivity.onClick: " + e.getMessage());
                	}
                    }
                });
        try {
	    GMP.init();
            initPi();
        }
        catch (Exception e) {            
	    Log.d("PI_Task", "MainActivity.onCreate: " + e.getMessage());
        }
    }

    public void display(String line)
    {
        mView.append(line);
        mView.append("\n");
    }
    
    public Object getRef()
    {
	return refPi;
    }
    
    private void initPi()
        throws Exception
    {
	String line;
	int i;
	int j;
	StringBuffer sb = new StringBuffer();
	BufferedReader br = new BufferedReader(new InputStreamReader(this.getResources().openRawResource(R.raw.pi)));
	for (;;) {
	    line = br.readLine();
	    if (line == null) break;
	    i = 0;
	    while (line.charAt(i) == ' ' ||
		   line.charAt(i) == '\t' ||
		   line.charAt(i) == '\r' ||
		   line.charAt(i) == '\n') i++;
	    j = line.length() - 1;
	    while (line.charAt(j) == ' ' ||
		   line.charAt(j) == '\t' ||
		   line.charAt(j) == '\r' ||
		   line.charAt(j) == '\n') j--;
	    sb.append(line.substring(i, j + 1));
	}
	refPi = new mpf_t();
	refPiStr = sb.toString();
	GMP.mpf_set_prec(refPi, (long)((double)refPiStr.length()*BITS_PER_DIGIT+16));
	Log.d("PI_Task", "initPi: ref=" + refPiStr);
	GMP.mpf_set_str(refPi, refPiStr, 10);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	// Inflate the menu; this adds items to the action bar if it is present.
	getMenuInflater().inflate(R.menu.main, menu);
	return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	// Handle action bar item clicks here. The action bar will
	// automatically handle clicks on the Home/Up button, so long
	// as you specify a parent activity in AndroidManifest.xml.
	int id = item.getItemId();
	if (id == R.id.action_settings) {
	    return true;
	}
	return super.onOptionsItemSelected(item);
    }
}
