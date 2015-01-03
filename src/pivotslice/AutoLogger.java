package pivotslice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class AutoLogger {
	
	private PrintWriter  output;
	private Date startTime;
	private boolean isEnabled;
	
	public AutoLogger(boolean enable) {
		isEnabled = enable;
	}
	
	public void start(){
		if (isEnabled) {
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
			try {
				startTime = Calendar.getInstance().getTime();
				output = new PrintWriter(new File(dateFormat.format(startTime) + ".csv"));
				logAction("logging starts");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void stop(){
		if (isEnabled) {
			logAction("logging ends");
			output.close();
		}
	}
	
	public void logAction(String msg) {
		if (!isEnabled)
			return;
		Date nowTime = Calendar.getInstance().getTime();
		long mseconds = nowTime.getTime() - startTime.getTime();
		output.println(mseconds + "," + msg);
	}

}
