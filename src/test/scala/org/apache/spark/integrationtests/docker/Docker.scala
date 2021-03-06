/*
 * Copyright 2015 Databricks Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.integrationtests.docker

import scala.collection.mutable
import scala.language.postfixOps
import scala.sys.process._

import fr.janalyse.ssh.{SSHOptions, SSH}

import org.apache.spark.Logging

object Docker extends Logging {
  private val runningDockerContainers = new mutable.HashSet[DockerId]()

  def registerContainer(containerId: DockerId) = this.synchronized {
    runningDockerContainers += containerId
  }

  def killAllLaunchedContainers() = this.synchronized {
    runningDockerContainers.foreach(kill)
  }

  /**
   * Launch a docker container.
   *
   * @param imageTag    the container image
   * @param args        arguments to pass to the container's default command (e.g. spark master url)
   * @param dockerArgs  arguments to pass to `docker` to control the container configuration
   * @param mountDirs   List of (localDirectory, containerDirectory) pairs for mounting directories
   *                    in container.
   * @return            A `DockerContainer` handle for interacting with the launched container.
   */
  def launchContainer(imageTag: String,
                      args: String = "",
                      dockerArgs: String = "",
                      mountDirs: Seq[(String, String)] = Seq.empty): DockerContainer = {
    val mountCmd = mountDirs.map{ case (s, t) => s"-v $s:$t" }.mkString(" ")

    val dockerLaunchCommand = s"docker run --privileged -d $mountCmd $dockerArgs $imageTag $args"
    logDebug(s"Docker launch command is $dockerLaunchCommand")
    val id = new DockerId(dockerLaunchCommand.!!.trim)
    registerContainer(id)
    try {
      new DockerContainer(id)
    } catch {
      case t: Throwable =>
        kill(id)
        throw t
    }
  }

  def kill(dockerId: DockerId) = this.synchronized {
    "docker kill %s".format(dockerId.id).!
    runningDockerContainers -= dockerId
  }

  def getLogs(dockerId: DockerId): String = {
    s"docker logs ${dockerId.id}".!!
  }

  def dockerHostIp: String = "172.17.42.1" // default docker host ip

  def getHostSSHConnection: SSH = {
    val key = "id_boot2docker"
    SSH(SSHOptions(host = "localhost", username =  "docker", port = 2022, sshKeyFile = Some(key)))
  }
}

class DockerId(val id: String) extends AnyVal {
  override def toString: String = id
}
