/*

Copyright (C) 2014 - Gareth Edwards / Rapid Information Systems

gareth.edwards@rapid-is.co.uk


This file is part of RapidSOA.

RapidSOA is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version. The terms require you to include
the original copyright, and the license notice in all redistributions.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
in a file named "COPYING".  If not, see <http://www.gnu.org/licenses/>.

 */

package com.rapid.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.util.ArrayList;

import javax.servlet.ServletContext;

public class DataFactory {
	
	public static class Parameter {
		
		public static final int NULL = 1;
		public static final int STRING = 2;
		public static final int DATE = 3;
		public static final int INTEGER = 4;		
		public static final int FLOAT = 5;
		
		private int _type;
		private String _string;
		private Date _date;
		private int _int;		
		private float _float;
		
		public Parameter() {
			_type = NULL;
		}
		
		public Parameter(String value) {
			_type = STRING;
			_string = value;
		}
		
		public Parameter(Date value) {
			_type = DATE;
			_date = value;
		}
		
		public Parameter(int value) {
			_type = INTEGER;
			_int = value;
		}
		
		public Parameter(float value) {
			_type = FLOAT;
			_float = value;
		}
		
		public int getType() { return _type; }
		public String getString() { return _string; }
		public Date getDate() { return _date; }
		public int getInteger() { return _int; }		
		public float getFloat() { return _float; }
		
	}
		
	public static class Parameters extends ArrayList<Parameter> {
		
		private boolean _toUpperCase;
		
		public boolean getToUpperCase() { return _toUpperCase; }
		public void setToUpperCase(boolean toUpperCase) { _toUpperCase = toUpperCase; }
		
		public void addNull() { this.add(new Parameter()); }
		public void addString(String value) { this.add(new Parameter(value)); }
		public void addInt(int value) { this.add(new Parameter(value)); }
		public void addDate(Date value) { this.add(new Parameter(value)); }
		public void addFloat(float value) { this.add(new Parameter(value)); }
		
		public void add(String value)  { this.add(new Parameter(value)); }
		public void add(int value)  { this.add(new Parameter(value)); }
		public void add(Date value)  { this.add(new Parameter(value)); }
		public void add(Float value)  { this.add(new Parameter(value)); }
				
		public Parameters() {}
		public Parameters(boolean toUpperCase) { _toUpperCase = toUpperCase; }
		
	}
	
	private String _connectionString;
	private String _user;
	private String _password;
	private boolean _autoCommit;
	private Connection _connection; 
	private String _sql;
	private Statement _statement;
	private PreparedStatement _preparedStatement;
	private ResultSet _resultset;
	
	public DataFactory(String connectionString, String user, String password) {
		_connectionString = connectionString;
		_user = user;
		_password = password;
		_autoCommit = true;
	}
	
	public DataFactory(String connectionString, String user, String password, boolean autoCommit) {
		_connectionString = connectionString;
		_user = user;
		_password = password;
		_autoCommit = autoCommit;
	}
	
	public DataFactory(ServletContext servletContext) {
		_connectionString = servletContext.getInitParameter("jdbc.connectionstring");
		_user = servletContext.getInitParameter("jdbc.user");
		_password = servletContext.getInitParameter("jdbc.password");
		_autoCommit = true;
	}
	
	public DataFactory(ServletContext servletContext, boolean autoCommit) {
		_connectionString = servletContext.getInitParameter("jdbc.connectionstring");
		_user = servletContext.getInitParameter("jdbc.user");
		_password = servletContext.getInitParameter("jdbc.password");
		_autoCommit = autoCommit;
	}
	
	public Connection getConnection() throws SQLException, ClassNotFoundException {
		
		if (_connection == null) {
			
			Class.forName("oracle.jdbc.driver.OracleDriver");
			
			_connection = DriverManager.getConnection(_connectionString, _user, _password);
			
			_connection.setAutoCommit(_autoCommit);
			
		} 
		
		return _connection;
				
	}
			
	public ResultSet getResultSet(String sql) throws SQLException, ClassNotFoundException {
		
		_sql = sql;
		
		if (_connection == null) _connection = getConnection();
		
		if (_resultset != null) _resultset.close();
		
		if (_statement != null) _statement.close();		
		
		_statement = _connection.createStatement();
		
		_resultset = _statement.executeQuery(_sql);
		
		return _resultset;
				
	}
	
	public PreparedStatement getPreparedStatement(String sql, Parameters parameters) throws SQLException, ClassNotFoundException  {
		
		_sql = sql;
		
		if (_connection == null) _connection = getConnection();
		
		if (_resultset != null) _resultset.close();
		
		if (_preparedStatement != null) _preparedStatement.close();	
		
		_preparedStatement = _connection.prepareStatement(_sql);
		
		int i = 0;
		
		for (Parameter parameter : parameters) {
		
			i++;
			
			switch (parameter.getType()) {
			case Parameter.NULL : _preparedStatement.setNull(i, java.sql.Types.NULL); break;
			case Parameter.STRING : 											
				if (parameter.getString() == null) {
					_preparedStatement.setNull(i, java.sql.Types.NULL);
				} else {
					if (parameters.getToUpperCase()) {
						_preparedStatement.setString(i, parameter.getString().toUpperCase());
					} else {
						_preparedStatement.setString(i, parameter.getString());
					}
				}
				break;
			case Parameter.DATE : 				
				if (parameter.getDate() == null) {
					_preparedStatement.setNull(i, java.sql.Types.NULL);
				} else {
					_preparedStatement.setDate(i, parameter.getDate());
				}
				break;
			case Parameter.INTEGER : _preparedStatement.setInt(i, parameter.getInteger()); break;
			case Parameter.FLOAT : _preparedStatement.setFloat(i, parameter.getFloat()); break;
			}						
		}
		
		return _preparedStatement;
		
	}
	
	public ResultSet getPreparedResultSet(String sql, Parameters parameters) throws SQLException, ClassNotFoundException {
		
		_resultset = getPreparedStatement(sql, parameters).executeQuery();
		
		return _resultset;
				
	}
	
	public String getPreparedScalar(String sql, Parameters parameters) throws SQLException, ClassNotFoundException {
		
		_resultset = getPreparedStatement(sql, parameters).executeQuery();
		
		String result = null;
		
		if (_resultset.next()) result = _resultset.getString(1);
		
		return result;
				
	}
	
	public int getPreparedUpdate(String sql, Parameters parameters) throws SQLException, ClassNotFoundException {
		
		return getPreparedStatement(sql, parameters).executeUpdate();
		
	}	
	
	public void commit() throws SQLException {
		
		if (_connection != null) _connection.commit();
		
	}
	
	public void rollback() throws SQLException {
		
		if (_connection != null) _connection.rollback();
		
	}
	
	public void close() throws SQLException {
										
		if (_resultset != null) _resultset.close();
		
		if (_statement != null) _statement.close();
		
		if (_preparedStatement != null) _preparedStatement.close();
		
		if (_connection != null) _connection.close();
		
	}

}
