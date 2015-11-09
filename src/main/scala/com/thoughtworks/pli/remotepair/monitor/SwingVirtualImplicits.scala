package com.thoughtworks.pli.remotepair.monitor

import java.awt.event.{ActionEvent, ActionListener, WindowAdapter, WindowEvent}
import javax.swing._
import javax.swing.event.{TreeSelectionEvent, TreeSelectionListener, ListSelectionEvent, ListSelectionListener}
import javax.swing.tree.{DefaultTreeModel, DefaultMutableTreeNode}

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

  implicit class RichTextField(input: JTextField) {
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

  class RichTextArea(textarea: JTextArea) {
    def text: String = textarea.getText
    def text_=(text: String) = textarea.setText(text)
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

  class RichList[E](list: JList[E]) {
    def items: Seq[E] = {
      val model = list.getModel
      (0 until model.getSize).map(model.getElementAt).toList
    }
    def items_=(values: Seq[E]): Unit = {
      val listModel = new DefaultListModel[E]()
      values.foreach(listModel.addElement)
      list.setModel(listModel)
    }
    def removeItems(values: Seq[E]): Unit = {
      val listModel = list.getModel.asInstanceOf[DefaultListModel[E]]
      values.foreach(listModel.removeElement)
    }
    def requestFocus(): Unit = list.requestFocus()
    def getSelectedValue: E = list.getSelectedValue
    def itemCount = list.getModel.getSize
    def isSelectedOnLastItem = itemCount == list.getSelectedIndex + 1
    def selectLastItem() = list.setSelectedIndex(itemCount - 1)
    def selectItem(item: E) = list.setSelectedValue(item, true)
    def clearItems(): Unit = list.setModel(new DefaultListModel[E]())
    def onSelect(f: () => Unit): Unit = list.addListSelectionListener(new ListSelectionListener {
      override def valueChanged(e: ListSelectionEvent): Unit = f()
    })
  }

  class RichTree(tree: JTree) {
    def getSelectedUserObject: Option[AnyRef] = {
      Option(tree.getSelectionPath).map(_.getLastPathComponent.asInstanceOf[DefaultMutableTreeNode].getUserObject)
    }
    def clear(): Unit = tree.setModel(null)
    def onSelect(f: => Any) = {
      tree.addTreeSelectionListener(new TreeSelectionListener {
        override def valueChanged(e: TreeSelectionEvent): Unit = f
      })
    }
    def setNodes(treeRoot: TreeNode): Unit = {
      def iterate(thisNode: TreeNode): DefaultMutableTreeNode = thisNode match {
        case Branch(data, children) => {
          val treeNode = new DefaultMutableTreeNode(data)
          children.foreach(child => treeNode.add(iterate(child)))
          treeNode
        }
        case Leaf(data) => new DefaultMutableTreeNode(data)
      }
      tree.setModel(new DefaultTreeModel(iterate(treeRoot)))
    }
  }

}

sealed trait TreeNode
case class Branch(data: AnyRef, children: Seq[TreeNode]) extends TreeNode
case class Leaf(data: AnyRef) extends TreeNode
