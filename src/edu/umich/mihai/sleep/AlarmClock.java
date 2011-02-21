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
import java.text.SimpleDateFormat;
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
import javax.swing.JTextArea;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import april.util.GetOpt;

public class AlarmClock implements ActionListener, ListSelectionListener
{
    private final double VERSION = 0.1;
    private final int SNOOZE = 10 * 60 * 1000; // in milliseconds
    private final int HOURS = 7;
    private final int MINUTES = 30;

    Runtime run = Runtime.getRuntime();
    private JFrame frame;

    private JButton addOffset;
    private JButton snooze;
    private JTextArea hours;
    private JTextArea minutes;

    private JTextArea eventYear;
    private JTextArea eventMonth;
    private JTextArea eventDay;
    private JTextArea eventHr;
    private JTextArea eventMin;
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
        hours = new JTextArea(HOURS + "");
        minutes = new JTextArea(MINUTES + "");
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 6;
        constraints.gridy = 0;
        frame.getContentPane().add(hours, constraints);
        constraints.gridx++;
        frame.getContentPane().add(new JLabel(":"), constraints);
        constraints.gridx++;
        frame.getContentPane().add(minutes, constraints);
        constraints.gridx++;
        frame.getContentPane().add(addOffset, constraints);
        constraints.gridx++;
        frame.getContentPane().add(snooze, constraints);

        setDate();
        eventHr = new JTextArea("09");
        eventMin = new JTextArea("00");
        add = new JButton("Add Event");
        set = new JButton("Set Alarm!");
        constraints.gridy++;
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
        constraints.gridx++;
        frame.getContentPane().add(set, constraints);

        days = new JCheckBox[labels.length];
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.gridx += 2;
        constraints.gridy = 0;
        for (int x = 0; x < labels.length; x++)
        {
            days[x] = new JCheckBox(labels[x]);
            frame.getContentPane().add(days[x], constraints);
            constraints.gridy++;
        }

        listModel = new DefaultListModel();
        events = new JList(listModel);
        events.setCellRenderer(new CustomListCellRenderer());
        constraints.gridy -= 5;
        constraints.gridx = 0;
        constraints.gridwidth = 10;
        constraints.gridheight = 10;
        frame.getContentPane().add(events, constraints);
        constraints.gridy++;
        
        hours.setColumns(2);
        minutes.setColumns(2);
        eventYear.setColumns(4);
        eventMonth.setColumns(2);
        eventDay.setColumns(2);
        eventHr.setColumns(2);
        eventMin.setColumns(2);

        addOffset.addActionListener(this);
        snooze.addActionListener(this);
        add.addActionListener(this);
        set.addActionListener(this);
        events.addListSelectionListener(this);

        frame.setSize(500, 250);
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
            events.setCellRenderer(new CustomListCellRenderer());
            entries.remove(entry);
            
            if(entry.isEnabled())
            {
                play();
            }
            
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
                    events.setCellRenderer(new CustomListCellRenderer());

                    break;
                }
            }
        }

        private void play()
        {
            mainEntry = entry;

            try
            {
                run.exec("banshee-1 --play " + song);
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
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat dayFormat = new SimpleDateFormat("E");
            String day = dayFormat.format(cal.getTime());
            int dayIndex = 0;
            
            for (int x = 0; x < labels.length; x++)
            {
                if (day.equals(labels[x]))
                {
                    dayIndex = x;
                    break;
                }
            }
            
            return dayIndex;
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
            events.setCellRenderer(new CustomListCellRenderer());
        }
        else if (source == snooze)
        {
            stop(mainEntry, false, true);
            start(mainEntry, SNOOZE);
        }
        else if (source == add)
        {
            AlarmEntry entry = new AlarmEntry();
            start(entry, getDate(eventYear, eventMonth, eventDay, eventHr, eventMin));
            entries.add(entry);

            listModel.addElement(getDate(eventYear, eventMonth, eventDay, eventHr, eventMin));
            events = new JList(listModel);
            events.setCellRenderer(new CustomListCellRenderer());
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

    private int getSeconds(JTextArea hours, JTextArea minutes)
    {
        return (Integer.parseInt(hours.getText()) * 3600) + (Integer.parseInt(minutes.getText()) * 60);
    }

    private Date getDate(JTextArea year, JTextArea month, JTextArea day, JTextArea hours, JTextArea minutes)
    {
        Calendar cal = Calendar.getInstance();

        cal.set(Integer.parseInt(year.getText()), Integer.parseInt(month.getText()) - 1, Integer.parseInt(day.getText()), Integer.parseInt(hours.getText()), Integer.parseInt(minutes.getText()));

        return cal.getTime();
    }

    private Date getOffsetDate(JTextArea hours, JTextArea minutes)
    {
        int hrs = Integer.parseInt(hours.getText());
        int min = Integer.parseInt(minutes.getText());

        return new Date(System.currentTimeMillis() + (hrs * 3600 * 1000) + (min * 60 * 1000));
    }

    private void setDate()
    {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis() + (1000 * 60 * 60 * 24));
        SimpleDateFormat curYr = new SimpleDateFormat("yyyy");
        SimpleDateFormat curMo = new SimpleDateFormat("MM");
        SimpleDateFormat curDay = new SimpleDateFormat("dd");

        eventYear = new JTextArea(curYr.format(cal.getTime()));
        eventMonth = new JTextArea(curMo.format(cal.getTime()));
        eventDay = new JTextArea(curDay.format(cal.getTime()));
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
        listModel.remove(entries.indexOf(entry));
        entries.remove(entry);
        events = new JList(listModel);
        events.setCellRenderer(new CustomListCellRenderer());
        
        Runtime run = Runtime.getRuntime();
        
        if(stopMusic)
        {
            try
            {
                run.exec("banshee-1 --stop");
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        if (stopTimer)
        {
            entry.getTimer().cancel();
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent arg0)
    {}
    
}
