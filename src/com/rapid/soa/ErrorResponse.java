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

package com.rapid.soa;

import java.sql.SQLException;

import com.rapid.soa.WSFactory.*;

public class ErrorResponse {
	
	// this class is used regularly - it processes Oracle messages into code and description automatically
	
	private String _errorCode, _errorMessage;
	
	@XSDorder(1)
	@XSDmaxLength(10)
	@XSDminOccurs(0)
	public String getErrorCode() { return _errorCode; }
	public void setErrorCode(String errorCode) { _errorCode = errorCode; }
	
	@XSDorder(2)
	@XSDmaxLength(1000)
	public String getErrorMessage() { return _errorMessage; }
	public void setErrorMessage(String errorMessage) { _errorMessage = errorMessage; }
		
	public ErrorResponse(String message) {
		_errorMessage = message;
	}
	
	public ErrorResponse(String code, String message) {
		_errorCode = code;
		_errorMessage = message;
	}
	
	private String cleanOracleLine(String line) {
		
		String[] errorParts = line.split(":");
		
		if (errorParts.length > 1) {
			return errorParts[1];
		} else {
			return line;
		}
		
	}
	
	private String cleanOracleMessage(SQLException ex) {
		
		String message = ex.getMessage();
		
		String[] errorLines = message.split("\n");
		
		String error = "";
		
		// show all lines if below custom error range
		if (ex.getErrorCode() < 20000) {
		
			for (String errorLine : errorLines) {
				
				error += cleanOracleLine(errorLine);
				
			}
		
		} else {
			
			// remove the line number
			
			String[] errorParts1 = errorLines[0].split(":");
			
			String[] errorParts2 = errorParts1[1].split("@");
			
			error = errorParts2[0].trim();
			
		}
		
		return error;
		
	}
	
	public ErrorResponse(Exception exception) {
		
		// retrieve the message from the exception
		_errorMessage = exception.getMessage();
		
		// get the cause message if one isn't obvious
		if (_errorMessage == null) _errorMessage = exception.getCause().toString();
		
		// if caused by a SQLException
		if (exception.getClass() == SQLException.class) {
			
			SQLException sqlexception = (SQLException) exception;
			
			_errorCode = Integer.toString(sqlexception.getErrorCode());
			
			// clean up oracle error messages
			if (_errorMessage.indexOf("ORA-") == 0) _errorMessage = cleanOracleMessage(sqlexception);							
			
		} else {
			
			_errorCode = "0";
	
		}
		
	}
	
}
