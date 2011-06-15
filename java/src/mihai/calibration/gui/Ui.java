package mihai.calibration.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;

public class Ui extends JFrame implements ActionListener, Broadcaster.Listener
{
    private static final long serialVersionUID = 1L;
    
    private Config config;
    
    private int currentCard = 0;
    private final static int MIN = 0;
    private final static int MAX = 6;
    private final static int SIZE = 7;
    private final static int INTRO = 0;
    private final static int CONFIG = 1;
    private final static int CAMERAS = 2;
    private final static int SETTINGS = 3;
    private final static int INTRINSICS = 4;
    private final static int EXTRINSICS = 5;
    private final static int TRACK = 6;
    
    private Broadcaster[] cards = new Broadcaster[SIZE];
    private JPanel mainPanel;
    
    private JButton nextButton;
    private JButton backButton;
    private String next = "Next";
    private String back = "Back";

    public Ui()
    {
        super("Mult-Camera Calibrator");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        try
        {
            cards[INTRO] = new IntroPanel(INTRO);
            cards[CONFIG] = new ConfigPanel(CONFIG);
            cards[CAMERAS] = new IntroPanel(CAMERAS);
            cards[SETTINGS] = new IntroPanel(SETTINGS);
            cards[INTRINSICS] = new IntroPanel(INTRINSICS);
            cards[EXTRINSICS] = new IntroPanel(EXTRINSICS);
            cards[TRACK] = new IntroPanel(TRACK);
            
            for(Broadcaster b : cards)
            {
                b.setListener(this);
            }
            cards[TRACK] = new IntroPanel(TRACK);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
        JPanel welcomePanel = new JPanel(new BorderLayout());
        JLabel welcome = new JLabel("Mulit-Camera Calibrator", JLabel.CENTER);
        welcome.setFont(new Font("Serif", Font.PLAIN, 32));
        welcomePanel.add(welcome, BorderLayout.NORTH);
        welcomePanel.add(new JLabel("presented by the APRIL Lab", JLabel.CENTER), BorderLayout.CENTER);
        welcomePanel.add(new JSeparator(), BorderLayout.SOUTH);
        
        nextButton = new JButton(next);
        nextButton.addActionListener(this);
        backButton = new JButton(back);
        backButton.addActionListener(this);
        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
        buttonBox.add(backButton);
        buttonBox.add(Box.createHorizontalStrut(10));
        buttonBox.add(nextButton);
        buttonBox.add(Box.createHorizontalStrut(30));
        JSeparator separator = new JSeparator();
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(separator, BorderLayout.NORTH);
        buttonPanel.add(buttonBox, java.awt.BorderLayout.EAST);

        add(welcomePanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.SOUTH);
        setCurrentCard(INTRO);
        
        setVisible(true);
    }
    
    public static void main(String[] args)
    {
        new Ui();
    }

    public void setCurrentCard(int card)
    {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screenSize.width, screenSize.height);
        nextButton.setEnabled(true);
        backButton.setEnabled(true);
        
        if(mainPanel != null)
        {
            ((Broadcaster)mainPanel).stop();
            getContentPane().remove(mainPanel);
        }
        try
        {
            switch(card)
            {
                case INTRO:
                    backButton.setEnabled(false);
                    setSize(screenSize.width/2, screenSize.height);
                    break;
                case CONFIG:
                    nextButton.setEnabled(false);
                    break;
                case CAMERAS:
                    
                    break;
                case SETTINGS:
                    
                    break;
                case INTRINSICS:
                    
                    break;
                case EXTRINSICS:
                    
                    break;
                case TRACK:
                    nextButton.setEnabled(false);
                    break;
                default:
                    throw new InvalidCardException();
            }
        } catch (InvalidCardException e)
        {
            e.printStackTrace();
        }

        mainPanel = cards[card];
        ((Broadcaster)mainPanel).go();
        add(mainPanel, BorderLayout.CENTER);
        
        currentCard = card;
    }
    
    public void gotoNextCard() throws InvalidCardException
    {
        if(currentCard+1 >= MIN && currentCard+1 <= MAX)
        {
            setCurrentCard(++currentCard);
        }
        else
        {
            throw new InvalidCardException();
        }
    }
    
    public void gotoLastCard() throws InvalidCardException
    {
        if(currentCard-1 >= MIN && currentCard-1 <= MAX)
        {
            setCurrentCard(--currentCard);
        }
        else
        {
            throw new InvalidCardException();
        }
    }
    
    public void actionPerformed(ActionEvent event)
    {
        if(event.getActionCommand().equals(next))
        {
            try
            {
                gotoNextCard();
            } catch (InvalidCardException e)
            {
                e.printStackTrace();
            }
        }
        else if(event.getActionCommand().equals(back))
        {
            try
            {
                gotoLastCard();
            } catch (InvalidCardException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void handle(int id, boolean ready, String info)
    {
        if(id == currentCard)
        {
            switch(id)
            {
                case INTRO:
                    break;
                case CONFIG:
                    try
                    {
                        config = new ConfigFile(info);
                        Util.verifyConfig(config);
                        nextButton.setEnabled(ready);
                    } catch (IOException e)
                    {
                        JOptionPane.showMessageDialog(this, "IO Exception: Unable to open file");
                        e.printStackTrace();
                        
                    } catch (ConfigException e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case CAMERAS:
                    break;
                case SETTINGS:
                    break;
                case INTRINSICS:
                    break;
                case EXTRINSICS:
                    break;
                case TRACK:
                    break;
                default:
                    try
                    {
                        throw new InvalidCardException();
                    } catch (InvalidCardException e)
                    {
                        e.printStackTrace();
                    }
            }
        }
    }

}
