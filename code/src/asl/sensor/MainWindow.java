package asl.sensor;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.*;


public class MainWindow extends JPanel implements ActionListener {

  private JButton loadButton1, loadButton2, loadButton3;
  private JFileChooser fc;
  private JTextArea statusBox;
  
  public MainWindow() {
    
    super( new BorderLayout() );
    
    statusBox = new JTextArea(10,50);
    statusBox.setMargin( new Insets(5,5,5,5) );
    statusBox.setEditable(false);
    JScrollPane statusScrollPane = new JScrollPane(statusBox);
    
    
    fc = new JFileChooser();
    
    loadButton1 = new JButton("Open file 1");
    loadButton1.addActionListener(this);
    
    loadButton2 = new JButton("Open file 2");
    loadButton2.addActionListener(this);
    
    loadButton3 = new JButton("Open file 3");
    loadButton3.addActionListener(this);
    // TODO: define additional panels for improving layout structure
    
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout( new BoxLayout(buttonPanel, BoxLayout.Y_AXIS) );
    buttonPanel.add(loadButton1);
    buttonPanel.add(loadButton2);
    buttonPanel.add(loadButton3);
    
    add(statusScrollPane);
    add(buttonPanel, BorderLayout.EAST);
  }
  
  public static void main(String[] args) {
    //Schedule a job for the event dispatch thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        //Turn off metal's use of bold fonts
        UIManager.put("swing.boldMetal", Boolean.FALSE); 
        createAndShowGUI();
      }
    });
    
  }
  

  private static void createAndShowGUI() {
    JFrame frame = new JFrame("File load");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    frame.add( new MainWindow() );
    
    frame.pack();
    frame.setVisible(true);
  }
  
  @Override
  public void actionPerformed(ActionEvent e) {
    // TODO: add more checks here as we add components
    
    if ( e.getSource() == loadButton1 ) {
      // originally used "MainWindow.this" as parameter
      int returnVal = fc.showOpenDialog(loadButton1);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
         File file = fc.getSelectedFile();
         statusBox.append("f1: " + file.getName() + "\n");
      }
    } else if ( e.getSource() == loadButton2 ) {
      // originally used "MainWindow.this" as parameter
      int returnVal = fc.showOpenDialog(MainWindow.this);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
         File file = fc.getSelectedFile();
         statusBox.append("f2: " + file.getName() + "\n");
      }
    } else if ( e.getSource() == loadButton3 ) {
      // originally used "MainWindow.this" as parameter
      int returnVal = fc.showOpenDialog(loadButton3);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
         File file = fc.getSelectedFile();
         statusBox.append("f3: " + file.getName() + "\n");
      }
    }
  }

}