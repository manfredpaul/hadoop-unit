/*
 * Copyright (c) 2011 Khanh Tuong Maudoux <kmx.petals@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package fr.jetoile.hadoopunit.component;

import com.github.sakserv.minicluster.impl.HiveLocalServer2;
import com.github.sakserv.minicluster.util.FileUtils;
import com.github.sakserv.minicluster.util.WindowsLibsUtils;
import fr.jetoile.hadoopunit.Component;
import fr.jetoile.hadoopunit.Config;
import fr.jetoile.hadoopunit.HadoopUtils;
import fr.jetoile.hadoopunit.exception.BootstrapException;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HiveServer2Bootstrap implements Bootstrap {
    final public static String NAME = Component.HIVESERVER2.name();

    final private Logger LOGGER = LoggerFactory.getLogger(HiveServer2Bootstrap.class);

    private HiveLocalServer2 hiveLocalServer2;

    private State state = State.STOPPED;

    private Configuration configuration;
    private String host;
    private int port;
    private String derbyDirectory;
    private String scratchDirectory;
    private String warehouseDirectory;
    private String zookeeperConnectionString;
    private String hostMetastore;
    private int portMetastore;


    public HiveServer2Bootstrap() {
        if (hiveLocalServer2 == null) {
            try {
                loadConfig();
            } catch (BootstrapException e) {
                LOGGER.error("unable to load configuration", e);
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getProperties() {
        return "[" +
                "port:" + port +
                "]";
    }

    private void loadConfig() throws BootstrapException {
        HadoopUtils.setHadoopHome();
        try {
            configuration = new PropertiesConfiguration("default.properties");
        } catch (ConfigurationException e) {
            throw new BootstrapException("bad config", e);
        }
        host = configuration.getString(Config.HIVE_SERVER2_HOSTNAME_KEY);
        port = configuration.getInt(Config.HIVE_SERVER2_PORT_KEY);
        hostMetastore = configuration.getString(Config.HIVE_METASTORE_HOSTNAME_KEY);
        portMetastore = configuration.getInt(Config.HIVE_METASTORE_PORT_KEY);
        derbyDirectory = configuration.getString(Config.HIVE_METASTORE_DERBY_DB_DIR_KEY);
        scratchDirectory = configuration.getString(Config.HIVE_SCRATCH_DIR_KEY);
        warehouseDirectory = configuration.getString(Config.HIVE_WAREHOUSE_DIR_KEY);
        zookeeperConnectionString = configuration.getString(Config.ZOOKEEPER_HOST_KEY) + ":" + configuration.getInt(Config.ZOOKEEPER_PORT_KEY);

    }


    private void cleanup() {
        FileUtils.deleteFolder(derbyDirectory);
        FileUtils.deleteFolder(derbyDirectory.substring(derbyDirectory.lastIndexOf("/") + 1));

    }

    private void build() {
        hiveLocalServer2 = new HiveLocalServer2.Builder()
                .setHiveMetastoreDerbyDbDir(derbyDirectory)
                .setHiveServer2Hostname(host)
                .setHiveServer2Port(port)
                .setHiveMetastoreHostname(hostMetastore)
                .setHiveMetastorePort(portMetastore)
                .setHiveWarehouseDir(warehouseDirectory)
                .setHiveScratchDir(scratchDirectory)
                .setHiveConf(buildHiveConf())
                .setZookeeperConnectionString(zookeeperConnectionString)
                .build();

    }

    private HiveConf buildHiveConf() {
        // Handle Windows
        WindowsLibsUtils.setHadoopHome();

        HiveConf hiveConf = new HiveConf();
//        hiveConf.set(HiveConf.ConfVars.HIVE_TXN_MANAGER.varname, "org.apache.hadoop.hive.ql.lockmgr.DbTxnManager");
//        hiveConf.set(HiveConf.ConfVars.HIVE_COMPACTOR_INITIATOR_ON.varname, "true");
//        hiveConf.set(HiveConf.ConfVars.HIVE_COMPACTOR_WORKER_THREADS.varname, "5");
//        hiveConf.set("hive.root.logger", "DEBUG,console");
//        hiveConf.setIntVar(HiveConf.ConfVars.METASTORETHRIFTCONNECTIONRETRIES, 3);
//        hiveConf.set(HiveConf.ConfVars.PREEXECHOOKS.varname, "");
//        hiveConf.set(HiveConf.ConfVars.POSTEXECHOOKS.varname, "");
//        System.setProperty(HiveConf.ConfVars.PREEXECHOOKS.varname, " ");
//        System.setProperty(HiveConf.ConfVars.POSTEXECHOOKS.varname, " ");

        return hiveConf;
    }

    @Override
    public Bootstrap start() {
        if (state == State.STOPPED) {
            state = State.STARTING;
            LOGGER.info("{} is starting", this.getClass().getName());

            build();
            try {
                hiveLocalServer2.start();
            } catch (Exception e) {
                LOGGER.error("unable to add hiveserver2", e);
            }
            state = State.STARTED;
            LOGGER.info("{} is started", this.getClass().getName());
        }
        return this;
    }

    @Override
    public Bootstrap stop() {
        if (state == State.STARTED) {
            state = State.STOPPING;
            LOGGER.info("{} is stopping", this.getClass().getName());
            try {
                hiveLocalServer2.stop(true);
            } catch (Exception e) {
                LOGGER.error("unable to stop hiveserver2", e);
            }
            cleanup();
            state = State.STOPPED;
            LOGGER.info("{} is stopped", this.getClass().getName());
        }
        return this;
    }

    @Override
    public org.apache.hadoop.conf.Configuration getConfiguration() {
        return hiveLocalServer2.getHiveConf();
    }


}
