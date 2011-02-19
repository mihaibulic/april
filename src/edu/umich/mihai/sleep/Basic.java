package edu.umich.mihai.sleep;

import java.awt.ComponentOrientation;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import april.util.GetOpt;

public class Basic implements ActionListener
{
    String[] COLUMNS = {"Month", "Year", "Date", "Time"};
    
    private final double VERSION = 0.1;
    private final int SNOOZE = 1000;
    private final int HOURS = 0;
    private final int MINUTES = 1;

    Runtime run = Runtime.getRuntime();
    private JFrame frame;

    private boolean on = false;
    private JButton set;
    private JButton snooze;
    private JTextArea hours;
    private JTextArea minutes;

    private JTextArea eventYear;
    private JTextArea eventMonth;
    private JTextArea eventDay;
    private JTextArea eventHr;
    private JTextArea eventMin;
    
    private JList events;
    private DefaultListModel listModel;
    private JButton add;

    ArrayList<Timer> timers = new ArrayList<Timer>();
    Timer mainTimer = new Timer();

    public Basic()
    {
        frame = new JFrame("Smart Alarm v" + VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        frame.getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();

        set = new JButton("Set Alarm!");
        snooze = new JButton("Snoozzzze");
        hours = new JTextArea(HOURS + "");
        minutes = new JTextArea(MINUTES + "");
        setDate();
        eventHr = new JTextArea("09");
        eventMin = new JTextArea("00");
        listModel = new DefaultListModel();
        events = new JList(listModel);
        add = new JButton("Add Event");
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 6;
        constraints.gridy = 0;
        frame.getContentPane().add(hours, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel(":"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(minutes, constraints);
        constraints.gridx++;
        frame.getContentPane().add(set, constraints);
        constraints.gridx++;
        frame.getContentPane().add(snooze, constraints);

        constraints.gridy += 2;
        constraints.gridx = 0;
        frame.getContentPane().add(eventMonth, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel("/"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(eventDay, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel("/"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(eventYear, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel(" "), constraints);
        constraints.gridx++;
        frame.getContentPane().add(eventHr, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel(":"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(eventMin, constraints);
        constraints.gridx++;
        frame.getContentPane().add(add, constraints);
        constraints.gridy += 2;
        constraints.gridx = 0;
        constraints.gridwidth = 11;
        frame.getContentPane().add(events, constraints);

        hours.setColumns(2);
        minutes.setColumns(2);
        eventYear.setColumns(4);
        eventMonth.setColumns(2);
        eventDay.setColumns(2);
        eventHr.setColumns(2);
        eventMin.setColumns(2);

        set.addActionListener(this);
        snooze.addActionListener(this);
        add.addActionListener(this);
        frame.setSize(500, 250);
        frame.setVisible(true);
    }

    class Alarm extends TimerTask
    {
        String song = "home/april/Desktop/play.mp3";
        Timer timer;

        public Alarm(Timer timer)
        {
            this.timer = timer;
        }

        public void run()
        {
            String cmd = "banshee-1 --play ";
            play(cmd);
        }

        private void play(String cmd)
        {
            mainTimer = timer;

            try
            {
                run.exec(cmd);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public void setSong(String song)
        {
            this.song = song;
        }
    }

    public static void main(String[] args)
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");

        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: Smart alarm");
            opts.doHelp();
            System.exit(1);
        }

        new Basic();
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
        Object source = event.getSource();

        if (source == set)
        {
            if (on)
            {
                set.setText("Set Alarm!");
                stop(mainTimer, true);
            }
            else
            {
                set.setText("Stop Alarm!");
                Timer timer = new Timer();
                start(timer, getSeconds(hours, minutes));
                timers.add(timer);
                
                listModel.addElement(getOffsetDate(hours, minutes));
                events = new JList(listModel);

                mainTimer = timer;
            }
            on = !on;
        }
        else if (source == snooze)
        {
            stop(mainTimer, false);
            start(mainTimer, SNOOZE);
        }
        else if (source == add)
        {
            Timer timer = new Timer();
            start(timer, getDate(eventYear, eventMonth, eventDay, eventHr, eventMin));
            timers.add(timer);

            listModel.addElement(getDate(eventYear, eventMonth, eventDay, eventHr, eventMin).toString());
            events = new JList(listModel);
        }

    }

    private int getSeconds(JTextArea hours, JTextArea minutes)
    {
        return (Integer.parseInt(hours.getText()) * 3600) + (Integer.parseInt(minutes.getText()) * 60);
    }

    private Date getDate(JTextArea year, JTextArea month, JTextArea day, JTextArea hours, JTextArea minutes)
    {
        Calendar cal = Calendar.getInstance();

        cal.set(Integer.parseInt(year.getText()), 
        Integer.parseInt(month.getText())-1, Integer.parseInt(day.getText()), 
        Integer.parseInt(hours.getText()), Integer.parseInt(minutes.getText()));
        
        return cal.getTime();
    }

    private Date getOffsetDate(JTextArea hours, JTextArea minutes)
    {
        int hrs = Integer.parseInt(hours.getText());
        int min = Integer.parseInt(minutes.getText());
        
        return new Date(System.currentTimeMillis()+(hrs*3600*1000)+(min*60*1000));
    }
    
    private void setDate()
    {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis()+(1000*60*60*24));
        SimpleDateFormat curYr = new SimpleDateFormat("yyyy");
        SimpleDateFormat curMo = new SimpleDateFormat("MM");
        SimpleDateFormat curDay = new SimpleDateFormat("dd");
        
        eventYear = new JTextArea(curYr.format(cal.getTime()));
        eventMonth = new JTextArea(curMo.format(cal.getTime()));
        eventDay = new JTextArea(curDay.format(cal.getTime()));
    }
    
    private void start(Timer timer, int seconds)
    {
        timer.schedule(new Alarm(timer), seconds * 1000);
    }

    private void start(Timer timer, Date date)
    {
        timer.schedule(new Alarm(timer), date);
    }
    
    private void stop(Timer timer, boolean stopTimer)
    {
        Runtime run = Runtime.getRuntime();
        try
        {
            run.exec("banshee-1 --stop");
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        if(stopTimer)
        {
            timer.cancel();
        }
    }
}
