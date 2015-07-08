/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.lens.ml.api;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.lens.api.LensSessionHandle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/*
 * Batch prediction Instance
 */

/**
 * Contains meta data for an Prediction. Prediction captures the meta data of process of batch predicting data of
 * inputDataSet against the ModelInstance modelInstanceId and populating results in outputDataSet.
 */
@AllArgsConstructor
@XmlRootElement
public class Prediction implements MLProcess {
  @XmlElement
  @Getter
  @Setter
  String id;

  @XmlElement
  @Getter
  @Setter
  Date startTime;

  @XmlElement
  @Getter
  @Setter
  Date finishTime;

  @XmlElement
  @Getter
  @Setter
  Status status;

  @Getter
  @Setter
  @XmlElement
  LensSessionHandle lensSessionHandle;

  @Getter
  @Setter
  @XmlElement
  String modelInstanceId;

  @Getter
  @Setter
  @XmlElement
  String inputDataSet;

  @Getter
  @Setter
  @XmlElement
  String outputDataSet;

}
