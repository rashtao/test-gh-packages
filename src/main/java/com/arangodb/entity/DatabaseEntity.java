/*
 * DISCLAIMER
 *
 * Copyright 2016 ArangoDB GmbH, Cologne, Germany
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
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */

package com.arangodb.entity;

/**
 * @author Mark Vollmary
 * 
 * @see <a href=
 *      "https://www.arangodb.com/docs/stable/http/database-database-management.html#information-of-the-database">API
 *      Documentation</a>
 */
public class DatabaseEntity implements Entity {

	private String id;
	private String name;
	private String path;
	private Boolean isSystem;
	private final ReplicationFactor replicationFactor;
	private final MinReplicationFactor minReplicationFactor;
	private String sharding;

	public DatabaseEntity() {
		super();
		replicationFactor = new ReplicationFactor();
		minReplicationFactor = new MinReplicationFactor();
	}

	/**
	 * @return the id of the database
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the name of the database
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the filesystem path of the database
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return whether or not the database is the _system database
	 */
	public Boolean getIsSystem() {
		return isSystem;
	}

	/**
	 * @return TODO
	 * @since ArangoDB 3.6.0
	 */
	public Integer getReplicationFactor() {
		return replicationFactor.getReplicationFactor();
	}

	/**
	 * @return TODO
	 * @since ArangoDB 3.6.0
	 */
	public Integer getMinReplicationFactor() {
		return minReplicationFactor.getMinReplicationFactor();
	}

	/**
	 * @return TODO
	 * @since ArangoDB 3.6.0
	 */
	public Boolean getSatellite() {
		return this.replicationFactor.getSatellite();
	}

	/**
	 * @return TODO
	 * @since ArangoDB 3.6.0
	 */
	public String getSharding() {
		return sharding;
	}
}
