package edu.umich.mihai.sleep;

import java.util.Date;
import java.util.Timer;

public class AlarmEntry
{
    private Timer timer;
    private boolean enabled;
    private Date date;

    public AlarmEntry()
    {
        timer = new Timer();
        enabled = true;
    }

    public AlarmEntry(Timer timer, Date date)
    {
        this.timer = timer;
        this.date = date;
        enabled = true;
    }

    public Date getDate()
    {
        return date;
    }
    
    public Timer getTimer()
    {
        return timer;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setDate(Date date)
    {
        this.date = date;
    }
    
    public void setTimer(Timer timer)
    {
        this.timer = timer;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
    
    public String toString()
    {
        return date.toString();
    }

}
