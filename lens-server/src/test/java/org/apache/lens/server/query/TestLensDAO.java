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
package org.apache.lens.server.query;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.core.Application;

import org.apache.lens.api.LensConf;
import org.apache.lens.api.LensSessionHandle;
import org.apache.lens.api.Priority;
import org.apache.lens.api.query.FailedAttempt;
import org.apache.lens.api.query.LensQuery;
import org.apache.lens.api.query.QueryHandle;
import org.apache.lens.api.query.QueryStatus;
import org.apache.lens.driver.hive.EmbeddedThriftConnection;
import org.apache.lens.driver.hive.HiveDriver;
import org.apache.lens.driver.hive.ThriftConnection;
import org.apache.lens.driver.jdbc.JDBCDriver;
import org.apache.lens.driver.jdbc.JDBCResultSet;
import org.apache.lens.server.LensJerseyTest;
import org.apache.lens.server.LensServices;
import org.apache.lens.server.api.LensConfConstants;
import org.apache.lens.server.api.driver.LensDriver;
import org.apache.lens.server.api.driver.MockDriver;
import org.apache.lens.server.api.driver.hooks.DriverQueryHook;
import org.apache.lens.server.api.error.LensException;
import org.apache.lens.server.api.query.*;
import org.apache.lens.server.api.user.MockDriverQueryHook;

import org.apache.hadoop.conf.Configuration;

import org.codehaus.jackson.map.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

/**
 * The Class TestLensDAO.
 */
@Test(groups = "unit-test")
@Slf4j
public class TestLensDAO extends LensJerseyTest {

  @Override
  protected Application configure() {
    return new TestQueryService.QueryServiceTestApp();
  }

  /**
   * Test lens server dao.
   *
   * @throws Exception the exception
   */
  @Test
  public void testLensServerDAO() throws Exception {
    QueryExecutionServiceImpl service = LensServices.get().getService(QueryExecutionService.NAME);
    String driverQuery = "SELECT aliasName1.Id FROM driverTable1 aliasName1";
    String userQuery = "SELECT ID FROM testTable";

    // Test insert query
    QueryContext queryContext = service.createContext(userQuery, "foo@localhost", new LensConf(),
            new Configuration(), 0);
    long submissionTime = queryContext.getSubmissionTime();
    queryContext.setQueryName("daoTestQuery1");
    queryContext.getDriverContext().setSelectedDriver(new MockDriver());
    queryContext.getLensConf().addProperty("prop", "value");

    LensDriver mockDriver = new MockDriver();
    DriverSelectorQueryContext mockDriverContext = new DriverSelectorQueryContext(userQuery,
            new Configuration(), Lists.newArrayList(mockDriver), false);

    queryContext.setDriverContext(mockDriverContext);
    queryContext.getDriverContext().setSelectedDriver(mockDriver);
    // Add a query for the selected driver
    queryContext.getDriverContext().setDriverQuery(mockDriver, driverQuery);
    Assert.assertEquals(queryContext.getDriverContext().getSelectedDriverQuery(), driverQuery);

    FinishedLensQuery finishedLensQuery = new FinishedLensQuery(queryContext);
    finishedLensQuery.setStatus(QueryStatus.Status.SUCCESSFUL.name());
    finishedLensQuery.setPriority(Priority.NORMAL.toString());

    finishedLensQuery.setFailedAttempts(Lists.newArrayList(
      new FailedAttempt("driver1", 1.0, "progress full", "no error", 0L, 1L),
      new FailedAttempt("driver2", 1.0, "progress also full", "no error at all", 2L, 3L)
    ));
    // Validate JDBC driver RS Meta can be deserialized

    // Create a valid JDBCResultSet
    Connection conn = null;
    Statement stmt = null;
    final ObjectMapper MAPPER = new ObjectMapper();

    try {
      conn = service.lensServerDao.getConnection();
      stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
      ResultSet rs = stmt.executeQuery("SELECT handle FROM finished_queries");

      JDBCResultSet jdbcResultSet = new JDBCResultSet(null, rs, false);
      JDBCResultSet.JDBCResultSetMetadata jdbcRsMeta =
        (JDBCResultSet.JDBCResultSetMetadata) jdbcResultSet.getMetadata();

      String jsonMetadata = MAPPER.writeValueAsString(jdbcRsMeta);

      log.info("@@@JSON {}" + jsonMetadata);

      finishedLensQuery.setMetadata(MAPPER.writeValueAsString(jdbcRsMeta));
    } catch (SQLException ex) {
      log.error("Error creating result set ", ex);
    } finally {
      if (stmt != null) {
        stmt.close();
      }
      if (conn != null) {
        conn.close();
      }
    }

    String finishedHandle = finishedLensQuery.getHandle();

    service.lensServerDao.insertFinishedQuery(finishedLensQuery);
    // Re-insert should be a no-op on the db.
    service.lensServerDao.insertFinishedQuery(finishedLensQuery);

    FinishedLensQuery actual = service.lensServerDao.getQuery(finishedHandle);

    Assert.assertEquals(finishedLensQuery, actual);
    Assert.assertEquals(actual.getConf().getProperty("prop"), "value");

    // Try to read back result set metadata class, should not throw deserialize exception
    JDBCResultSet.JDBCResultSetMetadata actualRsMeta = MAPPER.readValue(actual.getMetadata(),
      JDBCResultSet.JDBCResultSetMetadata.class);
    // Assert
    Assert.assertNotNull(actualRsMeta, "Should be able to read back metadata for jdbc queries");
    // Validate metadata
    Assert.assertEquals(actualRsMeta.getColumns().size(), 1);
    Assert.assertEquals(actualRsMeta.getColumns().get(0).getName().toLowerCase(), "handle");

    Assert.assertEquals(actual.getHandle(), finishedHandle);
    Assert.assertEquals(Priority.valueOf(actual.getPriority()), Priority.NORMAL);
    Assert.assertEquals(actual.getDriverQuery(), driverQuery);
    // when driver list contains the selected driver, selected driver should get set correctly in context
    QueryContext retrievedQueryContext = actual.toQueryContext(new Configuration(),
      Lists.newArrayList(mockDriver));
    Assert.assertEquals(retrievedQueryContext.getSelectedDriverQuery(), driverQuery);

    // Test find finished queries
    LensSessionHandle session = service.openSession("foo@localhost", "bar", new HashMap<String, String>());

    List<QueryHandle> persistedHandles = service.lensServerDao.findFinishedQueries(null, null, null, null,
      submissionTime, System.currentTimeMillis());
    if (persistedHandles != null) {
      for (QueryHandle handle : persistedHandles) {
        LensQuery query = service.getQuery(session, handle);
        if (!handle.getHandleId().toString().equals(finishedHandle)) {
          Assert.assertTrue(query.getStatus().finished(), query.getQueryHandle() + " STATUS="
            + query.getStatus().getStatus());
        }
      }
    }

    System.out.println("@@ State = " + queryContext.getStatus().getStatus().name());
    List<QueryHandle> daoTestQueryHandles = service.lensServerDao.findFinishedQueries(
      Lists.newArrayList(QueryStatus.Status.valueOf(finishedLensQuery.getStatus())), queryContext.getSubmittedUser(),
      queryContext.getSelectedDriver().getFullyQualifiedName(), "daotestquery1", -1L, Long.MAX_VALUE);
    Assert.assertEquals(daoTestQueryHandles.size(), 1);
    Assert.assertEquals(daoTestQueryHandles.get(0).getHandleId().toString(), finishedHandle);

    service.lensServerDao.insertActiveQuery(queryContext);
    QueryContext actualQueryContext = service.lensServerDao.findActiveQueryDetails(queryContext.getQueryHandle());
    Assert.assertEquals(actualQueryContext.getQueryHandle().toString(), queryContext.getQueryHandle().toString());

    QueryContext invalidQueryContext = service.createContext("SELECT ID FROM testTable1",
            "foo@localhost", new LensConf(),
            new Configuration(), 0);

    try {
      QueryContext actualInvalidQueryContext =
              service.lensServerDao.findActiveQueryDetails(invalidQueryContext.getQueryHandle());
    } catch (LensException le) {
      // expected to throw LensException
    }

    boolean actualInvalidDeleteQuery = service.lensServerDao.deleteActiveQuery(invalidQueryContext);
    Assert.assertNotEquals(actualInvalidDeleteQuery, true);

    service.lensServerDao.insertActiveSession(service.getSession(session).getLensSessionPersistInfo());

    LensSessionHandle invalidSession = service.openSession("foo@localhost", "bar",
            new HashMap<String, String>());

    boolean actualActiveSessionDeleted = service.lensServerDao.deleteActiveSession(session);
    Assert.assertEquals(actualActiveSessionDeleted, false);

    boolean invalidActualActiveSessionDeleted = service.lensServerDao.deleteActiveSession(invalidSession);
    Assert.assertEquals(invalidActualActiveSessionDeleted, false);

    service.closeSession(session);
  }

  public void testPreparedQueryDAO() throws Exception {
    QueryExecutionServiceImpl service = LensServices.get().getService(QueryExecutionService.NAME);
    Connection conn = null;
    Statement stmt = null;
    final ObjectMapper MAPPER = new ObjectMapper();

    try {
      conn = service.lensServerDao.getConnection();
      stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
      ResultSet rs = stmt.executeQuery("SELECT handle FROM finished_queries");

      JDBCResultSet jdbcResultSet = new JDBCResultSet(null, rs, false);
      JDBCResultSet.JDBCResultSetMetadata jdbcRsMeta =
          (JDBCResultSet.JDBCResultSetMetadata) jdbcResultSet.getMetadata();

      String jsonMetadata = MAPPER.writeValueAsString(jdbcRsMeta);

      log.info("@@@JSON {}" + jsonMetadata);

    } catch (SQLException ex) {
      log.error("Error creating result set ", ex);
    } finally {
      if (stmt != null) {
        stmt.close();
      }
      if (conn != null) {
        conn.close();
      }
    }

    service.lensServerDao.createPreparedQueriesTable();
    Configuration driverConf = new Configuration();
    try {
      PreparedQueryContext preparedQueryContext = new PreparedQueryContext("query", "user1", driverConf,
          createDriver(driverConf));
      preparedQueryContext.setPrepareEndTime(new Date());
      service.lensServerDao.insertPreparedQuery(preparedQueryContext);
    } catch (Exception e) {
      Assert.fail("it shouldn't be coming in this catch block");
    }


  }

  protected Collection<LensDriver> createDriver(Configuration driverConf) throws LensException {

    driverConf.addResource("drivers/jdbc/jdbc1/jdbcdriver-site.xml");
    driverConf.setClass(HiveDriver.HIVE_CONNECTION_CLASS, EmbeddedThriftConnection.class, ThriftConnection.class);
    driverConf.setClass(LensConfConstants.DRIVER_HOOK_CLASSES_SFX, MockDriverQueryHook.class, DriverQueryHook.class);
    driverConf.set("hive.lock.manager", "org.apache.hadoop.hive.ql.lockmgr.EmbeddedLockManager");
    driverConf.setBoolean(HiveDriver.HS2_CALCULATE_PRIORITY, true);
    JDBCDriver driver = new JDBCDriver();
    driver.configure(driverConf, "jdbc", "jdbc1");
    Collection<LensDriver> drivers = Lists.<LensDriver>newArrayList(driver);

    System.out.println("TestJDBCDriver created");
    return drivers;
  }
}

