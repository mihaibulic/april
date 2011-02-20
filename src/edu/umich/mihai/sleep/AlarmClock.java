package edu.umich.mihai.sleep;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimerTask;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import april.util.GetOpt;
import april.util.TimeUtil;

public class AlarmClock implements ActionListener, ListSelectionListener
{
    private final double VERSION = 0.2;
    private final int SNOOZE = 10 * 60; // in seconds
    private final int HOURS = 7;
    private final int MINUTES = 30;

    Runtime run = Runtime.getRuntime();
    private JFrame frame;

    private JButton addOffset;
    private JButton snooze;
    private JButton stop;
    
    private JSpinner hours;
    private JSpinner minutes;

    private JSpinner eventTime;
    private JButton add;
    private JList events;
    private DefaultListModel listModel;

    private String labels[] = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
    private JCheckBox[] days;

    private JButton set;
    
    ArrayList<AlarmEntry> entries = new ArrayList<AlarmEntry>();
    AlarmEntry mainEntry = new AlarmEntry();

    public AlarmClock()
    {
        frame = new JFrame("Smart Alarm v" + VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        frame.getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();

        addOffset = new JButton("Add Offset Alarm");
        snooze = new JButton("Snoozzzze");
        stop = new JButton("Stop");
        hours = new JSpinner(new SpinnerNumberModel(HOURS,0,23,1));;
        minutes = new JSpinner(new SpinnerNumberModel(MINUTES,0,59,1));
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        frame.getContentPane().add(new JLabel("hours:"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(hours, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel("min:"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(minutes, constraints);
        constraints.gridx++;
        frame.getContentPane().add(addOffset, constraints);
        constraints.gridx++;
        frame.getContentPane().add(snooze, constraints);
        constraints.gridx++;
        frame.getContentPane().add(stop, constraints);
        
        setDate();
        add = new JButton("Add Event");
        set = new JButton("Set Alarm!");
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth=4;
        frame.getContentPane().add(eventTime, constraints);
        constraints.gridx+=4;
        constraints.gridwidth=1;
        frame.getContentPane().add(add, constraints);
        constraints.gridx++;
        frame.getContentPane().add(set, constraints);

        days = new JCheckBox[labels.length];
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.gridx += 3;
        constraints.gridy = 0;
        for (int x = 0; x < labels.length; x++)
        {
            days[x] = new JCheckBox(labels[x]);
            frame.getContentPane().add(days[x], constraints);
            constraints.gridy++;
        }

        listModel = new DefaultListModel();
        
        events = new JList(listModel);
        events.setVisibleRowCount(5);
        events.setCellRenderer(new CustomListCellRenderer());
        JScrollPane eventsPane = new JScrollPane(events);
        constraints.gridy -= 4;
        constraints.gridx = 0;
        constraints.gridwidth = 7;
        constraints.gridheight = 10;
        frame.getContentPane().add(eventsPane, constraints);
        constraints.gridy++;
        
        addOffset.addActionListener(this);
        snooze.addActionListener(this);
        stop.addActionListener(this);
        add.addActionListener(this);
        set.addActionListener(this);
        events.addListSelectionListener(this);

        snooze.setVisible(false);
        stop.setVisible(false);
        
        frame.setSize(650, 200);
        frame.setVisible(true);

        events.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent click)
            {
                if (click.isShiftDown())
                {
                    stop(entries.get(events.locationToIndex(click.getPoint())), true, false);
                }
                else
                {
                    if(entries.get(events.locationToIndex(click.getPoint())).isEnabled())
                    {
                        entries.get(events.locationToIndex(click.getPoint())).setEnabled(false);
                    }
                    else
                    {
                        entries.get(events.locationToIndex(click.getPoint())).setEnabled(true);
                    }
                }
            }
        });
    }

    private class CustomListCellRenderer extends DefaultListCellRenderer 
    {  
        private static final long serialVersionUID = 1L;

        public Component getListCellRendererComponent( JList list, Object value, int index, boolean isSelected, boolean cellHasFocus )
        {  
            Component c = super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
            
            if ( entries.get(index).isEnabled() ) 
            {
                c.setBackground( Color.green );  
            }  
            else 
            {  
                c.setBackground( Color.white );
            }
            
            return c;  
        }  
    }

    class Alarm extends TimerTask
    {
        String song = "home/april/Desktop/play.mp3";
        AlarmEntry entry;
        boolean[] repeat = new boolean[labels.length];
        
        public Alarm(AlarmEntry entry, boolean[] repeat)
        {
            this.entry = entry;
            for (int x = 0; x < labels.length; x++)
            {
                this.repeat[x] = repeat[x];
            }
        }
        
        public Alarm(AlarmEntry entry)
        {
            this.entry = entry;
            for (int x = 0; x < labels.length; x++)
            {
                repeat[x] = days[x].isSelected();
                days[x].setSelected(false);
            }
        }

        public void run()
        {
            listModel.remove(entries.indexOf(entry));
            events = new JList(listModel);
            entries.remove(entry);
            
            if(entry.isEnabled())
            {
                snooze.setVisible(true);
                stop.setVisible(true);

                int day = getDayOfWeek();
                for(int x = 1; x <= labels.length; x++)
                {
                    day = getNextDay(day);
                    if(repeat[day])
                    {
                        Date date = getDate(x);
                        AlarmEntry newEntry = new AlarmEntry();
                        start(newEntry,repeat,date);
                        entries.add(newEntry);
                        
                        listModel.addElement(date);
                        events = new JList(listModel);
                        
                        break;
                    }
                }
                
                play();
            }
            
        }

        private void play()
        {
            mainEntry = entry;

            try
            {
                run.exec("rhythmbox-client --no-start --set-volume=0 --play");
                for(double vol = 0; vol <= 1; vol+=0.005)
                {
                    if(!entry.isEnabled())
                    {
                        break;
                    }
                    TimeUtil.sleep(200);
                    run.exec("rhythmbox-client --no-start --set-volume="+vol);
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public void setSong(String song)
        {
            this.song = song;
        }

        private int getDayOfWeek()
        {
            return (Calendar.getInstance()).get(Calendar.DAY_OF_WEEK)-1;
        }
        
        private int getNextDay(int day)
        {
            return (day < 6) ? day+1 : 0;
        }
        
        private void start(AlarmEntry entry, boolean[] repeat, Date date)
        {
            (entry.getTimer()).schedule(new Alarm(entry, repeat), date);
        }
        
        private Date getDate(int days)
        {
            int oneDay = 1000*60*60*24; // milliseconds
            
            return new Date(System.currentTimeMillis() + oneDay*days);
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

        new AlarmClock();
    }

    @Override
    public void actionPerformed(ActionEvent event)
    {
        Object source = event.getSource();

        if (source == addOffset)
        {
            AlarmEntry entry = new AlarmEntry();
            start(entry, getSeconds(hours, minutes));
            entries.add(entry);
            listModel.addElement(getOffsetDate(hours, minutes));
            events = new JList(listModel);
        }
        else if (source == snooze)
        {
            stop(mainEntry, false, true);
            
            entries.add(mainEntry);
            listModel.addElement(getOffsetDate(SNOOZE));
            events = new JList(listModel);
            start(mainEntry, SNOOZE);
            mainEntry.setEnabled(true);
        }
        else if (source == stop)
        {
            stop(mainEntry, false, true);
        }
        else if (source == add)
        {
            AlarmEntry entry = new AlarmEntry();
            
            start(entry, ((SpinnerDateModel) eventTime.getModel()).getDate());
            entries.add(entry);

            listModel.addElement(((SpinnerDateModel) eventTime.getModel()).getDate());
            events = new JList(listModel);
        }
        else if (source == set)
        {
            Date date = (Date)listModel.get(0);
            entries.get(0).setEnabled(false);
            int earliest = 0;
            for(int x = 1; x < entries.size(); x++)
            {
                entries.get(x).setEnabled(false);
                if(date.after((Date)listModel.get(x)))
                {
                    date = (Date)listModel.get(x);
                    earliest = x;
                }
            }
            entries.get(earliest).setEnabled(true);
            frame.repaint();
        }
    }

    private int getSeconds(JSpinner hours, JSpinner minutes)
    {
        int hrs = ((SpinnerNumberModel) hours.getModel()).getNumber().intValue();
        int min = ((SpinnerNumberModel) minutes.getModel()).getNumber().intValue();
        
        return (hrs * 60 * 60) + (min * 60);
    }

    private Date getOffsetDate(JSpinner hours, JSpinner minutes)
    {
        int hrs = ((SpinnerNumberModel) hours.getModel()).getNumber().intValue();
        int min = ((SpinnerNumberModel) minutes.getModel()).getNumber().intValue();

        return new Date(System.currentTimeMillis() + (hrs * 3600 * 1000) + (min * 60 * 1000));
    }

    private Date getOffsetDate(int seconds)
    {
        return new Date(System.currentTimeMillis() + seconds*1000);
    }

    private void setDate()
    {
        Calendar calendar = Calendar.getInstance();
        Date minDate = calendar.getTime();
        
        if(calendar.get(Calendar.HOUR_OF_DAY) > 9)
        {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 00);
        Date initDate = calendar.getTime();
                
        calendar.add(Calendar.YEAR, 5);
        Date latestDate = calendar.getTime();
        
        eventTime = new JSpinner(new SpinnerDateModel(initDate, minDate, latestDate, Calendar.YEAR));
        eventTime.setEditor(new JSpinner.DateEditor(eventTime, "MM/dd/yyyy HH:mm"));
    }
    
    private void start(AlarmEntry entry, int seconds)
    {
        (entry.getTimer()).schedule(new Alarm(entry), seconds * 1000);
    }

    private void start(AlarmEntry entry, Date date)
    {
        (entry.getTimer()).schedule(new Alarm(entry), date);
    }

    private void stop(AlarmEntry entry, boolean stopTimer, boolean stopMusic)
    {
        if(stopTimer)
        {
            listModel.remove(entries.indexOf(entry));
            entries.remove(entry);
            events = new JList(listModel);
            entry.getTimer().cancel();
        }
        
        if(stopMusic)
        {
            snooze.setVisible(false);
            stop.setVisible(false);
            Runtime run = Runtime.getRuntime();
            entry.setEnabled(false);
            try
            {
                run.exec("rhythmbox-client --pause");
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent arg0)
    {}
    
}
