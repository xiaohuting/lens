package com.inmobi.grill.server.api.query;

/*
 * #%L
 * Grill API for server and extensions
 * %%
 * Copyright (C) 2014 Inmobi
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;

import org.apache.hadoop.fs.Path;

import com.inmobi.grill.server.api.driver.GrillResultSetMetadata;

/**
 * Query result formatter, if the result is persisted by driver
 *
 */
public interface PersistedOutputFormatter extends QueryOutputFormatter {

  /**
   * Add result rows from the persisted path
   *
   * @param persistedPath
   *
   * @throws IOException
   */
  public void addRowsFromPersistedPath(Path persistedPath)
      throws IOException;
  
}
