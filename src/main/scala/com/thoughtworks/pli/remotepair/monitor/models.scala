package com.thoughtworks.pli.remotepair.monitor

import com.thoughtworks.pli.intellij.remotepair.server.ClientIdName

object models {

  case class VersionItemData(version: Int, sourceClient: ClientIdName) {
    override def toString: String = sourceClient.name + ": " + version.toString
  }

}
