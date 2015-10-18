package com.thoughtworks.pli.remotepair.monitor

import java.awt.event.{ActionEvent, ActionListener, WindowAdapter, WindowEvent}
import javax.swing._

object SwingVirtualImplicits {

  implicit class RichButton(button: JButton) {
    def text_=(value: String): Unit = button.setText(value)
    def onClick(f: => Unit): Unit = button.addActionListener(new ActionListener {
      def actionPerformed(actionEvent: ActionEvent): Unit = f
    })
    def enabled_=(value: Boolean): Unit = button.setEnabled(value)
    def enabled: Boolean = button.isEnabled
    def requestFocus(): Unit = button.requestFocus()
  }

  implicit class RichInputField(input: JTextField) {
    def text: String = input.getText
    def trimmedText: String = text.trim
    def text_=(value: String): Unit = input.setText(value)
    def requestFocus(): Unit = input.requestFocus()
  }

  implicit class RichCheckBox(checkbox: JCheckBox) {
    def isSelected: Boolean = checkbox.isSelected
    def requestFocus(): Unit = checkbox.requestFocus()
  }

  implicit class RichDialog(dialog: JDialog) {
    def dispose(): Unit = dialog.dispose()
    def onClose(f: => Unit): Unit = dialog.addWindowListener(new WindowAdapter {
      override def windowClosed(windowEvent: WindowEvent): Unit = f
    })
    def onOpen(f: => Unit): Unit = dialog.addWindowListener(new WindowAdapter {
      override def windowOpened(windowEvent: WindowEvent): Unit = f
    })
    def title: String = dialog.getTitle
    def title_=(title: String): Unit = dialog.setTitle(title)
    def requestFocus(): Unit = dialog.requestFocus()
  }

  implicit class RichLabel(label: JLabel) {
    def text: String = label.getText
    def text_=(value: String): Unit = {
      label.setText(value)
    }
    def visible_=(value: Boolean): Unit = label.setVisible(value)
    def visible: Boolean = label.isVisible
    def requestFocus(): Unit = label.requestFocus()
  }

  implicit class RichProgressBar(progressBar: JProgressBar) {
    def max: Int = progressBar.getMaximum
    def max_=(value: Int): Unit = progressBar.setMaximum(value)
    def value_=(value: Int): Unit = progressBar.setValue(value)
    def value: Int = progressBar.getValue
    def requestFocus(): Unit = progressBar.requestFocus()
  }

  implicit class RichList(list: JList[String]) {
    def items: Seq[String] = {
      val model = list.getModel
      (0 until model.getSize).map(model.getElementAt).toList
    }
    def items_=(values: Seq[String]): Unit = {
      val listModel = new DefaultListModel[String]()
      values.foreach(listModel.addElement)
      list.setModel(listModel)
    }
    def selectedItems: Seq[String] = list.getSelectedValues.map(_.toString)
    def removeItems(values: Seq[String]): Unit = {
      val listModel = list.getModel.asInstanceOf[DefaultListModel[String]]
      values.foreach(listModel.removeElement)
    }
    def requestFocus(): Unit = list.requestFocus()
  }

}
