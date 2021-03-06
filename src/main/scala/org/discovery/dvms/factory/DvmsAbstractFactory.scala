package org.discovery.dvms.factory

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */

import org.discovery.dvms.monitor.AbstractMonitorActor
import org.discovery.peeractor.util.NodeRef
import org.discovery.dvms.entropy.AbstractEntropyActor
import org.discovery.dvms.dvms.{SchedulerActor, DvmsActor, LocalityBasedScheduler}
import org.discovery.dvms.log.LoggingActor
import org.discovery.dvms.service.ServiceActor
import org.discovery.peeractor.overlay.chord.ChordService
import org.discovery.peeractor.overlay.OverlayService

trait DvmsAbstractFactory {

   def createMonitorActor(nodeRef: NodeRef): Option[AbstractMonitorActor]

   def createDvmsActor(nodeRef: NodeRef, overlayService: OverlayService): Option[SchedulerActor]

   def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor]

   def createLoggingActor(nodeRef: NodeRef): Option[LoggingActor]

   def createServiceActor(nodeRef: NodeRef, overlayService: ChordService): Option[ServiceActor]
}
