package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.remotepair.monitor.models.Project

import scala.swing.{Button, BorderPanel}

class ProjectSimpleComponent(val project: Project) extends BorderPanel {

  import BorderPanel.Position._

  layout(new Button(project.name)) = Center

}
