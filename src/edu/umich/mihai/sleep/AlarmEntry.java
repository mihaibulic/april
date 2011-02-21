package edu.umich.mihai.sleep;

import java.util.Timer;

public class AlarmEntry
{
    private Timer timer;
    private boolean enabled;
    
    public AlarmEntry()
    {
        timer = new Timer();
        enabled = true;
    }
    
    public AlarmEntry(Timer timer)
    {
        this.timer = timer;
        enabled = true;
    }
    public Timer getTimer()
    {
        return timer;
    }
    public boolean isEnabled()
    {
        return enabled;
    }
    public void setTimer(Timer timer)
    {
        this.timer = timer;
    }
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
    
}
