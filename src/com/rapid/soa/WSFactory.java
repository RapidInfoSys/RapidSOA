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

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.log4j.Logger;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class WSFactory {
	
	private final static String NAMESPACE = "http://rapid-is.co.uk/soa/";
	private final static String NAMESPACE_PREFIX = "soar";
	
	public static abstract class Request {
		
		public abstract Object getResponse(ServletContext servletContext) throws Exception;

	}
	
	public class ClassNotRequestException 
		extends Exception {
		
		@Override
		public String getMessage() { return "All classes for the factory must extend WSFactory.Request"; }
		
	}
	
	public class ClassNotDirectlyInstantiable 
	extends Exception {
		
		Class _class;
		
		public ClassNotDirectlyInstantiable(Class c) {
			_class = c;
		}
	
		@Override
		public String getMessage() { return "All classes for the factory must be directly instantiable - maybe make class " + _class.getCanonicalName() + " static"; }
		
	}
	
	public class UnrecognizedOperation 
	extends Exception {
	
		@Override
		public String getMessage() { return "Operation not found. Please add."; }
	
	}
			
	private static class ElementAttribute {
		
		private String _name;
		private String _value;
		
		public String getName() { return _name; }
		public String getValue() { return _value; }
		
		public ElementAttribute(String name, String value) {
			_name = name;
			_value = value;
		}
		
		public ElementAttribute(String name, int value) {
			_name = name;
			_value = Integer.toString(value);
		}
		
		public ElementAttribute(String name, boolean value) {
			_name = name;
			_value = Boolean.toString(value);
		}
		
	}
	
	private static class ElementAttributes extends ArrayList<ElementAttribute> {
		
		public boolean contains(String name) {
			
			boolean contains = false;
			
			for (ElementAttribute a : this) {
				if (a.getName().equals(name)) {
					contains = true;
					break;
				}
			}
			
			return contains;
			
		}
		
	}
	
	private static boolean getIsSimpleSOAPType(Class c) {
		
		// is this a java class or any of the primitives
		return c.getName().indexOf("java") == 0 || 
				c.getName().equals("int") || 
				c.getName().equals("boolean") || 
				c.getName().equals("float") 
				? true : false;
		
	}
	
	private static String getSimpleSOAPType(Class c) {
		
		String type = "string";
		
		if (c.getSimpleName().equals("boolean")) { type = "boolean"; }
		else if (c.getSimpleName().equals("Date")) { type = "date"; }
		else if (c.getSimpleName().equals("Timestamp")) { type = "dateTime"; }
		else if (c.getSimpleName().equals("float") || c.getSimpleName().equals("Float")) { type = "decimal"; }
		else if (c.getSimpleName().equals("int") || c.getSimpleName().equals("Integer")) { type = "integer"; }
		 						
		return type;
		
	}
	
	private static class SOAPClassElement {
		
		private Class _class;
		private String _name;
		private String _type;
		private Boolean _complexType;
		private ElementAttributes _attributes;
		private ElementAttributes _restrictions;
		private String _arrayType;
		
		public Class getMethodClass() { return _class; }
		public String getName() { return _name; }
		public String getType() { return _type; }		
		public String getArrayType() { return _arrayType; }
		public ElementAttributes getAttributes() { return _attributes; }
		public ElementAttributes getRestrictions() { return _restrictions; }
		public Boolean isComplexType() { return _complexType; }
		public Boolean isArray() { return _class.isArray(); }
		
		private void init(Annotation[] annotations, Class c, String name) {
			
			_class = c;			
			_name = name;			
			_attributes = new ElementAttributes();
			_restrictions = new ElementAttributes();
			
			// get the type										
			if (_class.isArray()) {
					
				_type = "xsd:ArrayOf" + _name + "Type";
				
				if (getIsSimpleSOAPType(_class.getComponentType())) {
					
					_complexType = false;					
					_arrayType = "xs:" + getSimpleSOAPType(_class.getComponentType());
					
				} else {
					
					_complexType = true;
					_arrayType = "xsd:" + _class.getComponentType().getSimpleName();
					
				}
														
			} else {
				
				if (getIsSimpleSOAPType(_class)) {
					
					_complexType = false;
					_type = "xs:" + getSimpleSOAPType(_class);
					
				} else {
					
					_complexType = true;
					_type = "xsd:" + _class.getSimpleName();
					
				}
												
			}
			
			// get the annotations from the get method (which will describe our element more fully)
			// loop them							
			for (Annotation a : annotations) {
				
				if (a instanceof XSDname) {		
					
					XSDname t = (XSDname) a;							
					_name = t.name();
					
				} else if (a instanceof XSDtype) {
					
					XSDtype t = (XSDtype) a;							
					_type = t.name();
					
				} else if (a instanceof XSDminOccurs) {
					
					XSDminOccurs t = (XSDminOccurs) a;							
					_attributes.add(new ElementAttribute("minOccurs", t.value()));
					
				} else if (a instanceof XSDmaxOccurs) {
					
					XSDmaxOccurs t = (XSDmaxOccurs) a;							
					_attributes.add(new ElementAttribute("maxOccurs", t.value()));
					
				} else if (a instanceof XSDnillable) {
					
					XSDnillable t = (XSDnillable) a;							
					_attributes.add(new ElementAttribute("nillable", t.value()));
					
				} else if (a instanceof XSDminLength) {
					
					XSDminLength t = (XSDminLength) a;							
					_restrictions.add(new ElementAttribute("minLength", t.value()));
					
				} else if (a instanceof XSDmaxLength) {
					
					XSDmaxLength t = (XSDmaxLength) a;							
					_restrictions.add(new ElementAttribute("maxLength", t.value()));
					
				} else if (a instanceof XSDpattern) {
					
					XSDpattern t = (XSDpattern) a;							
					_restrictions.add(new ElementAttribute("pattern", t.value()));
					
				} else if (a instanceof XSDenumeration) {
					
					XSDenumeration t = (XSDenumeration) a;							
					_restrictions.add(new ElementAttribute("enumeration", t.value()));
					
				} else if (a instanceof XSDminInclusive) {
					
					XSDminInclusive t = (XSDminInclusive) a;							
					_restrictions.add(new ElementAttribute("minInclusive", t.value()));
					
				} else if (a instanceof XSDmaxInclusive) {
					
					XSDmaxInclusive t = (XSDmaxInclusive) a;							
					_restrictions.add(new ElementAttribute("maxInclusive", t.value()));
					
				} else if (a instanceof XSDminExclusive) {
					
					XSDminExclusive t = (XSDminExclusive) a;							
					_restrictions.add(new ElementAttribute("minExclusive", t.value()));
					
				} else if (a instanceof XSDmaxExclusive) {
					
					XSDmaxExclusive t = (XSDmaxExclusive) a;							
					_restrictions.add(new ElementAttribute("maxExclusive", t.value()));
					
				}
					
			}
			
		}
							
		public SOAPClassElement(Method m, String name) {			
			init(m.getAnnotations(), m.getReturnType(), name);			
		}
		
		public SOAPClassElement(Field f, String name) {			
			init(f.getAnnotations(), f.getType(), name);			
		}
								
	}
	
	private static class SOAPClass {

		private Class _baseClass;
		private ArrayList<SOAPClassElement> _SOAPClassElements;
		
		public Class getBaseClass() { return _baseClass; }		
		public ArrayList<SOAPClassElement> getSOAPClassElements() { return _SOAPClassElements; }
		
		public SOAPClass(Class baseClass) {
			_baseClass = baseClass;
			_SOAPClassElements = new ArrayList<SOAPClassElement>();
		}
				
		public SOAPClassElement getSOAPClassElement(String name) {
			
			SOAPClassElement returnElement = null;
			
			for (SOAPClassElement soapElement : _SOAPClassElements) {
				// the key/class name is the same or the declared name is the same
				if (soapElement.getName().equals(name)) {
					returnElement = soapElement;
					break;
				}
			}
			
			return returnElement;
			
		}
						
	}
	
	private class SOAPClasses extends Hashtable<String, SOAPClass> {
				
		// this is just to prevent the same thing being added more than once
		public boolean contains(String className) {
			
			boolean contains = false;
			
			for (String key : this.keySet()) {
				
				if (key.equals(className)) {
					
					contains = true;
					break;
					
				}
				
			}
			
			return contains;
			
		}
		
		@Override
		public SOAPClass put(String key, SOAPClass soapClass) {
			
			if (contains(key)) {
				return this.get(key);
			} else {
				return super.put(key, soapClass);
			}
			
		}
				
	}
		
	private static class ClassMethods extends ArrayList<Method> {
		
		public class MethodComparator implements Comparator<Method> {

			private int getOrder(Method m) {
				
				int order = 0;
				
				for (Annotation a : m.getAnnotations()) {
					if (a instanceof XSDorder) {
						XSDorder o = (XSDorder) a;
						order = o.value();
						break;
					}
				}
				
				return order;
				
			}
			
			public int compare(Method m1, Method m2) {
				
				int o = 0;
				int o1 = getOrder(m1);
				int o2 = getOrder(m2);
				
				if (o1 > o2) {
					o = 1;
				} else if (o1 < o2) {
					o = -1;
				}
				
				return o;
			}
			
		}
		
		public ClassMethods(Method[] methods) {
			
			Arrays.sort(methods, new MethodComparator());
			
			for (Method m : methods) this.add(m);
			
		}
		
		public Boolean contains(String name) {
			
			Boolean contains = false;
			
			for (Method m : this) {
				if (m.getName().endsWith(name)) {
					contains = true;
					break;
				}
			}
			
			return contains;
			
		}		
		
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
	public @interface XSDchoice {}
			
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDname { public String name(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDtype { public String name(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDorder { public int value(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDminOccurs { public int value(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDmaxOccurs { public int value(); }	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDnillable { public boolean value(); }	
		
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDminLength { public int value(); }	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDmaxLength { public int value(); }	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDpattern { public String value(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDenumeration { public String value(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDminInclusive{ public String value(); }	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDmaxInclusive { public String value(); }
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDminExclusive{ public String value(); }	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface XSDmaxExclusive { public String value(); }
		
	private static WSFactory _WSFactory;
	private SOAPClasses _soapClasses;
	private Hashtable<String, SOAPClass> _operations;
	private ArrayList<String> _complexTypes;
	private Hashtable<String,Document> _wsdls;			
	private Hashtable<String, Validator> _validators;	
	private Logger _logger; 
			
	private WSFactory() {
		// initialise collections
		_soapClasses = new SOAPClasses();
		_operations = new Hashtable<String, SOAPClass>();
		_complexTypes = new ArrayList<String>();
		_wsdls = new Hashtable<String,Document>();				
		_validators = new Hashtable<String, Validator>();
		// initialise logger
		_logger = Logger.getLogger(WSFactory.class);		
	}
		
	public static WSFactory getWSFactory() {
		// instantiate only if null (singleton pattern)
		if (_WSFactory == null) _WSFactory = new WSFactory();
		// return it
		return _WSFactory;		
	}
	
	private boolean isXSDAnnotation(Annotation a) {
		
		if (a instanceof XSDchoice || 
				a instanceof XSDname || 
				a instanceof XSDtype || 
				a instanceof XSDorder || 
				a instanceof XSDminOccurs || 
				a instanceof XSDmaxOccurs || 
				a instanceof XSDnillable || 
				a instanceof XSDminLength || 
				a instanceof XSDmaxLength|| 
				a instanceof XSDpattern || 
				a instanceof XSDenumeration || 
				a instanceof XSDminInclusive || 
				a instanceof XSDmaxInclusive || 
				a instanceof XSDminExclusive || 
				a instanceof XSDmaxExclusive ) {
			
			return true;
			
		} else {
			
			return false;
			
		}
				
	}
	
			
	private SOAPClass getSOAPClass(Class c) {
		
		// create soapClass for return
		SOAPClass soapClass = new SOAPClass(c);
		
		// names of all class methods
		ClassMethods methods = new ClassMethods(c.getMethods());	
		
		// loop all retained method names looking for get/set pairs
		for (Method m : methods) {
			// work with the get methods as we need the return type for our class
			if (m.getName().indexOf("get") == 0) {
				// if we have a corresponding set method in the list
				if (methods.contains("set" + m.getName().substring(3))) {
					
					// name
					String name = m.getName().substring(3);

					// make the property
					SOAPClassElement p = new SOAPClassElement(m, name);

					// put property in our collection
					soapClass.getSOAPClassElements().add(p);
					
					// add any properties that are complex types in too
					if (p.isComplexType() && !p.isArray()) {
						
						SOAPClass s = getSOAPClass(p.getMethodClass());
						
						addSoapClass(p.getMethodClass().getName(), s);
						
					}															
				}
			}						
		}
		
		Field[] fields = c.getFields();
		
		if (fields != null) {
			
			for (Field f : fields) {
				
				Annotation[] annotations = f.getAnnotations();
												
				if (annotations != null) {
					
					boolean soaField = false;
					
					for (Annotation a : annotations) {
						
						if (isXSDAnnotation(a)) {
							soaField = true;
							break;
						}
						
					}
					
					if (soaField) {
						
						// name
						String name = f.getName();

						// make the property
						SOAPClassElement p = new SOAPClassElement(f, name);

						// put property in our collection
						soapClass.getSOAPClassElements().add(p);
						
						// add any properties that are complex types in too
						if (p.isComplexType() && !p.isArray()) {
							
							SOAPClass s = getSOAPClass(p.getMethodClass());
							
							addSoapClass(p.getMethodClass().getName(), s);
							
						}
						
					}
					
				}
								
			}
									
		}
		
		return soapClass;
		
	}
	
	public void addSoapClass(String name, SOAPClass soapClass) {
		if (!_soapClasses.contains(name)) _soapClasses.put(name, soapClass);
	}
	
	public void addSubClassSoapClasses(Class c) 
			throws ClassNotDirectlyInstantiable {
		
		// check properties and add SOAPType if complex
		// names of all class methods
		ClassMethods methods = new ClassMethods(c.getMethods());	
		
		// loop all retained method names looking for get/set pairs
		for (Method m : methods) {
			// work with the get methods as we need the return type for our class
			if (m.getName().indexOf("get") == 0) {
				// if we have a corresponding set method in the list
				if (methods.contains("set" + m.getName().substring(3))) {
					
					Class mc = m.getReturnType();
					
					if (!getIsSimpleSOAPType(mc)) {
						
						if (mc.isArray()) mc = mc.getComponentType();
						
						addSoapClass(mc.getName(), getSOAPClass(mc));
						
						addSubClassSoapClasses(mc);
					
					}
										
				}
			}						
		}
		
		// check fields for XSD annotations and add SOAPType if complex
		Field[] fields = c.getFields();
		
		if (fields != null) {
			
			for (Field f : fields) {
				
				Annotation[] annotations = f.getAnnotations();
												
				if (annotations != null) {
					
					boolean soaField = false;
					
					for (Annotation a : annotations) {
						
						if (isXSDAnnotation(a)) {
							soaField = true;
							break;
						}
						
					}
					
					if (soaField) {
						
						Class fc = f.getType();
						
						if (!getIsSimpleSOAPType(fc)) {
							
							if (fc.isArray()) fc = fc.getComponentType();
							
							String name = fc.getName();
							
							SOAPClass soapClass = getSOAPClass(fc);
						
							addSoapClass(name, soapClass);
							
							addSubClassSoapClasses(fc);
						
						}
						
					}
					
				}
								
			}
									
		}
		
	}
		
	public void clearOperations() {
		
		_operations.clear();
		_wsdls.clear();
		_validators.clear();
				
	}
			
	public void addOperation(String operationName, String requestClassName) 
			throws	ClassNotFoundException, 
			ClassNotRequestException, 
			SecurityException, 
			UnrecognizedOperation, 
			ParserConfigurationException, 
			NoSuchMethodException, 
			ClassNotDirectlyInstantiable {

		Class c = Class.forName(requestClassName);		
		
		// make sure this class extends WSFactory.Request
		if (c.getSuperclass().equals(Request.class)) {
			
			// get the SOAPClass
			SOAPClass s = getSOAPClass(c);
			
			// add the request class
			addSoapClass(c.getName(), s);
									
			// add all sub classes
			addSubClassSoapClasses(c);
			
			Method[] methods = c.getMethods();
			
			// locate and add the getResponse class
			for (Method m : methods) {
				
				if (m.getName().equals("getResponse") && !m.getReturnType().getName().equals("java.lang.Object")) {
					
					Class rc = m.getReturnType();					
					
					// if it's not a simple type
					if (!getIsSimpleSOAPType(rc)) {
						
						// create a soap type for it
						addSoapClass(rc.getName(), getSOAPClass(rc));
						
						// add all sub classes
						addSubClassSoapClasses(rc);
						
					}
										
					break;
				}
				
			}
			
			// add the operation
			_operations.put(operationName, s);
			
			// log it
			_logger.debug("Operation " + operationName + " added");
			
		} else { throw new ClassNotRequestException(); }
				
	}		
	
	public void addOperation(String operationName, Class requestClass) 
			throws	ClassNotFoundException, 
			ClassNotRequestException, 
			SecurityException, 
			UnrecognizedOperation, 
			ParserConfigurationException, 
			NoSuchMethodException, 
			ClassNotDirectlyInstantiable {
		
		addOperation(operationName, requestClass.getCanonicalName());
		
	}
	
	public Set<String> getOperations() { return _operations.keySet(); }
	
	private Element getElement(Document doc, SOAPClassElement p, Element schemaRoot) {
		
		Element propertyelement = doc.createElement("xs:element");
				
		if (p.isArray()) {
			
			//complex type for array
			Element arrayComplexType = doc.createElement("xs:complexType");
			arrayComplexType.setAttribute("name", "ArrayOf" + p.getName() + "Type");
			
			Element arraySequence = doc.createElement("xs:sequence");
			arrayComplexType.appendChild(arraySequence);
			
			Element arrayElement = doc.createElement("xs:element");
			arraySequence.appendChild(arrayElement);
			arrayElement.setAttribute("name", p.getName());		
			
			// this is an array so maxOccours should be unbounded unless otherwise set
			boolean maxOccursSet = false;
			
			// set the attributes here			
			for (ElementAttribute a : p.getAttributes()) {
				
				// if minOccurs is zero make the whole array optional
				if (a.getName().equals("minOccurs") && a.getValue().equals("0")) {
					
					propertyelement.setAttribute(a.getName(), a.getValue());
					
				} else if (a.getName().equals("maxOccurs")) {
					
					maxOccursSet = true;
					propertyelement.setAttribute(a.getName(), a.getValue());
					
				} else {
					
					arrayElement.setAttribute(a.getName(), a.getValue());
					
				}				
			}
						
			if (!maxOccursSet) arrayElement.setAttribute("maxOccurs","unbounded");
						
			schemaRoot.appendChild(arrayComplexType);
			
			// name
			propertyelement.setAttribute("name", "ArrayOf" + p.getName());
			
			// type
			propertyelement.setAttribute("type", p.getType());
			
			
			//type
			if (p.isComplexType()) {
				
				// type
				arrayElement.setAttribute("type", p.getArrayType());
				// create a complex type for what the array is enclosing
				Element complexType = getComplexType(doc, p.getMethodClass().getComponentType(), false, schemaRoot);
				if (complexType != null) schemaRoot.appendChild(complexType);
				
			} else {
				
				boolean gotRestrictions = false;
				
				// any restrictions here
				for (ElementAttribute r : p.getRestrictions()) {
					
					if (r.getName().equals("enumeration")) {
						
						// create the simple type
						Element simpletypeElement = doc.createElement("xs:simpleType");					
						arrayElement.appendChild(simpletypeElement);
						
						// create the restriction with the base as type
						Element restrictionElement = doc.createElement("xs:restriction");
						restrictionElement.setAttribute("base", p.getArrayType());
						simpletypeElement.appendChild(restrictionElement);
						
						String[] values = r.getValue().split(",");
						for (String v : values) {
							Element re = doc.createElement("xs:enumeration");
							re.setAttribute("value", v);
							restrictionElement.appendChild(re);	
						}
						
						gotRestrictions = true;
						
					}
					
				}
				
				// if no restrictions go simple
				if (!gotRestrictions) arrayElement.setAttribute("type", p.getArrayType());
				
			}
																							
			
		} else {
			
			// name
			propertyelement.setAttribute("name", p.getName());	
						
			// create and append complex type
			if (p.isComplexType()) {
			
				Element complexType = getComplexType(doc, p.getMethodClass(), false, schemaRoot);
				
				propertyelement.setAttribute("type", p.getType());
				
				if (complexType != null) schemaRoot.appendChild(complexType);
				
			} else {
				
				if (p.getRestrictions().size() > 0) {
					
					// create the simple type
					Element simpletypeElement = doc.createElement("xs:simpleType");					
					propertyelement.appendChild(simpletypeElement);
					
					// create the restriction with the base as type
					Element restrictionElement = doc.createElement("xs:restriction");
					restrictionElement.setAttribute("base", p.getType());
					simpletypeElement.appendChild(restrictionElement);
					
					// add any restrictions
					for (ElementAttribute r : p.getRestrictions()) {
						
						if (r.getName().equals("enumeration")) {
							
							String[] values = r.getValue().split(",");
							for (String v : values) {
								Element re = doc.createElement("xs:enumeration");
								re.setAttribute("value", v);
								restrictionElement.appendChild(re);	
							}
							
						} else {
							
							Element re = doc.createElement("xs:" + r.getName());
							re.setAttribute("value", r.getValue());
							restrictionElement.appendChild(re);	
							
						}
																		
					}
					
				} else {
					
					// just set the type
					propertyelement.setAttribute("type", p.getType());
					
				}
				
			}
			
			// add remaining non-special attributes
			for (ElementAttribute at : p.getAttributes()) {
				propertyelement.setAttribute(at.getName(), at.getValue());
			}						
			
		}
									
		return propertyelement;
		
	}
	
	private Element getComplexType(Document doc, Class c, Boolean anonymous, Element schemaRoot) {
		
		if (_complexTypes.contains(c.getSimpleName()) && !anonymous) {
			
			return null;
			
		} else {
		
			Element complextype = doc.createElement("xs:complexType");
	        if (!anonymous) complextype.setAttribute("name", c.getSimpleName());
	        
	        SOAPClass soapClass = _soapClasses.get(c.getName());
	            
	        // assume this is a sequence
	        boolean choice = false;
	        
	        // check the annotation on the class and set to choice
	        if (c.getAnnotations() != null ) {
	        	for (Annotation a : c.getAnnotations()) {
	        		if (a instanceof XSDchoice) choice = true;
	        	}
	        }
	        // check the properties if not yet choice
	        if (!choice) {
	        	int choiceCount = 0;
		        int propertyCount = 0;
		        ArrayList<String> methods = new ArrayList<String>();
		        // check get/set
		        for (Method m : c.getMethods()) {
		        	String methodName = m.getName();
		        	if (methodName.startsWith("get") || methodName.startsWith("set")) {
		        		methodName = methodName.substring(3);
		        		if (methods.contains(methodName)) {
		        			propertyCount ++;
		        		} else {
		        			methods.add(methodName);
		        		}
			        	for (Annotation a : m.getAnnotations()) {
			        		if (a instanceof XSDchoice) choiceCount ++;	        			        		
			        	}
		        	}	        				
				}
		        // check fields
		        for (Field f : c.getFields()) {
		        	boolean countedProperty = false;
		        	for (Annotation fa : f.getAnnotations()) {
		        		// count whether choice
		        		if (fa instanceof XSDchoice) choiceCount ++;		        		
		        		// count whether XSD
		        		if (isXSDAnnotation(fa) && !countedProperty) {
		        			propertyCount ++;
		        			countedProperty = true;
		        		}
		        	}
		        }
		        if (choiceCount > 0 && choiceCount == propertyCount) choice = true;
	        }
	        
	        // create the element accordingly
	        Element e;	        
	        if (choice) {
	        	e = doc.createElement("xs:choice");	        	
	        } else {
	        	e = doc.createElement("xs:sequence");
	        }	        
	        complextype.appendChild(e);
	        
	        // add it's children
			for (SOAPClassElement p : soapClass.getSOAPClassElements()) {
																
				e.appendChild(getElement(doc, p, schemaRoot));
				
			}
			
			_complexTypes.add(c.getSimpleName());
			
			return complextype;
						
		}
        		
	}
			
	private Document getRequestSchemaPrivate(String operationName) 
			throws UnrecognizedOperation, 
			ParserConfigurationException {
				 					
		SOAPClass sReq = _operations.get(operationName);
		
		if (sReq == null) throw new UnrecognizedOperation();
		
		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
		
		dbfac.setNamespaceAware(true);
		
        DocumentBuilder db = dbfac.newDocumentBuilder();
        
        DOMImplementation di = db.getDOMImplementation();
        
        Document doc = di.createDocument("http://www.w3.org/2001/XMLSchema", "xs:schema", null);
        
        Element schemaRoot = doc.getDocumentElement();
        schemaRoot.setAttribute("targetNamespace", NAMESPACE);
        schemaRoot.setAttribute("xmlns:xsd", NAMESPACE);
        schemaRoot.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");        
        schemaRoot.setAttribute("elementFormDefault", "qualified");
        //schemaRoot.setAttribute("attributeFormDefault", "unqualified");
        
        // create element for class
        Element request = doc.createElement("xs:element");
        request.setAttribute("name", sReq.getBaseClass().getSimpleName());
        request.appendChild(getComplexType(doc, sReq.getBaseClass(), true, schemaRoot));
        
        schemaRoot.appendChild(request);
                                                           
        return doc;
		
	}
	
	public Document getRequestSchema(String operationName) 
			throws UnrecognizedOperation, 
			ParserConfigurationException {
				
		_complexTypes.clear();
		
		Document requestSchema = getRequestSchemaPrivate(operationName);
		
		return requestSchema;
					
	}
	
 	private Element getWSDLSchema(Document document, Class c) 
			throws ParserConfigurationException, 
			SecurityException, 
			NoSuchMethodException, 
			ClassNotFoundException {
		
		Element schemaRoot = document.createElement("xs:schema");
		
		schemaRoot.setAttribute("targetNamespace", NAMESPACE);
		schemaRoot.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
		schemaRoot.setAttribute("elementFormDefault", "qualified");
        				
        // create element for request
        Element request = document.createElement("xs:element");
        request.setAttribute("name", c.getSimpleName());
        request.appendChild(getComplexType(document, c, true, schemaRoot));
        schemaRoot.appendChild(request);
                                               
        // find the request element
        Method[] requestMethods = c.getDeclaredMethods();
        Method requestMethod = null;
        for (Method m : requestMethods) {
        	if (m.getName().equals("getResponse") && !m.getReturnType().getName().equals("java.lang.Object")) {
        		requestMethod = m;
        		break;
        	}
        }
                
        // create an element for the response
        Element response = document.createElement("xs:element");
        // set the name
        response.setAttribute("name", requestMethod.getReturnType().getSimpleName());
        // check for simple / complex return type
        Class resClass = requestMethod.getReturnType();
        // only create an element if complex type
        if (getIsSimpleSOAPType(resClass)) {            	
        	// set the type
        	response.setAttribute("type", "xs:" + getSimpleSOAPType(resClass));
        } else {        	
        	// add the complex type
        	response.appendChild(getComplexType(document, resClass, true, schemaRoot));  
        }
        
        // add the element
        schemaRoot.appendChild(response);
        
		return schemaRoot;
		
	}
	
 	private Object getElementObject(SOAPClassElement p, Node n) 
 			throws SecurityException, 
 			IllegalArgumentException, 
 			DOMException, 
 			ClassNotFoundException, 
 			InstantiationException, 
 			IllegalAccessException, 
 			NoSuchMethodException,
 			NoSuchFieldException, 
 			InvocationTargetException, 
 			ParseException {
 		
 		Object j = null;
 		
 		if (p.isArray() && p.isComplexType()) {
 			
			// make an object from component type and add as child
			j = getObject(n, p.getMethodClass().getComponentType().getName());
 				 		
 		} else {
 		 					
			if (p.isComplexType()) {
				
				// make an object and add as child
				j = getObject(n, p.getMethodClass().getName());
				
			} else {
				
				// check for casting into int, dateTime, date, etc	
				if (p.getType().equals("xs:boolean")) {
					
					j = Boolean.parseBoolean(n.getTextContent());
					
				} else if (p.getType().equals("xs:date")) {
														
					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
					
					java.util.Date d = df.parse(n.getTextContent());
					
					j = new java.sql.Date(d.getTime());						
					
				} else if (p.getType().equals("xs:dateTime")) {
					
					SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
					
					java.util.Date d = df.parse(n.getTextContent());
					
					j = new java.sql.Date(d.getTime());							
					
				} else if (p.getType().equals("xs:decimal")) {
					
					j = Float.parseFloat(n.getTextContent());
					
				} else if (p.getType().equals("xs:integer")) {
					
					j = Integer.parseInt(n.getTextContent());
					
				} else {
					
					j = n.getTextContent();
					
				}
			
			}
		
 		}
 		 		
		return j;
 		
 	}
 	
	private Object getObject(Node node, String className) 
			throws ClassNotFoundException, 
			InstantiationException, 
			IllegalAccessException, 
			SecurityException, 
			IllegalArgumentException, 
			DOMException, 
			NoSuchMethodException,
			NoSuchFieldException,
			InvocationTargetException, 
			ParseException {
		
		SOAPClass soapClass = _soapClasses.get(className);
		
		Class c = soapClass.getBaseClass();
		
		Object o = c.newInstance();
						
		NodeList nodes = node.getChildNodes();
		
		for (int i = 0; i < nodes.getLength(); i++) {
			
			Node n = nodes.item(i);
			
			if (n.getNodeType() == Node.ELEMENT_NODE) {
																
				String elementName = n.getLocalName();
				
				SOAPClassElement p = soapClass.getSOAPClassElement(elementName);
				
				// look for setter method
				
				Method[] methods = o.getClass().getMethods();
				Method method = null;
								
				if (elementName.indexOf("ArrayOf") == 0) {
					
					NodeList cnodes = n.getChildNodes();
					
					if (cnodes.getLength() > 0) {
						
						elementName = elementName.substring(7);
						
						// search for the modified setter name here
						for (Method m : methods) {
							if (("set" + elementName).equals(m.getName())) {
								if (m.getReturnType().equals(p.getMethodClass())) {
									method = m;
									break;
								}							
							}
						}
						
						p = soapClass.getSOAPClassElement(elementName);
						
						ArrayList l = new ArrayList();						
													
						for (int j = 0; j < cnodes.getLength(); j ++) {
							
							Node cn = cnodes.item(j);
							
							if (cn.getNodeType() == Node.ELEMENT_NODE) {
								
								l.add(getElementObject(p, cn));
								
							}
							
						}
						
						Object oa = Array.newInstance(p.getMethodClass().getComponentType(), l.size());	
						
						System.arraycopy(l.toArray(), 0, oa, 0, l.size());
						
						if (method == null) {
							
							o.getClass().getField(elementName).set(o, oa);
							
						} else {
												
							method.invoke(o, oa);
							
						}
						
					}
					
				} else {
					
					for (Method m : methods) {
						if (("set" + elementName).equals(m.getName())) {
							method = m;
							break;							
						}
					}
														
					if (method == null) {
						
						o.getClass().getField(elementName).set(o, getElementObject(p, n));
						
					} else {
						
						method.invoke(o, getElementObject(p, n));
						
					}
					
				} // array or not
				
			}
			
		}
		
		return o;
		
	}

	private String getXMLValue(SOAPClassElement p, Object o) {
		
		// get the type of this SOAPClassElement
		String elementType = p.getType();
		
		// promote the type if this is an array
		if (p.isArray()) elementType = p.getArrayType();
		
		// do checks for special formats
		if (elementType.equals("xs:date")) {
			
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			
			return df.format(o);						
			
		} else if (elementType.equals("xs:dateTime")) {
			
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			
			return df.format(o);						
								
		} else {
			
			return o.toString();
			
		}
		
	}
	
	private void addObjectElement(SOAPElement parentElement, SOAPClassElement p, Object o) 
	throws SOAPException, 
	SecurityException, 
	NoSuchMethodException, 
	NoSuchFieldException,
	IllegalArgumentException, 
	IllegalAccessException, 
	InvocationTargetException {
		
		// look for getter method		
		Method[] methods = o.getClass().getMethods();
		Method method = null;
		for (Method m : methods) {
			if (("get" + p.getName()).equals(m.getName())) {
				method = m;
				break;												
			}
		}
		
		// the value we want				
		Object v = null;
		
		// the name of return type class
		String returnTypeClassName = null;
		
		// get the value object using the field if we couldn't find the method
		if (method == null) {
			Field f = o.getClass().getField(p.getName());
			v = f.get(o);
			returnTypeClassName = f.getType().getName();
		} else {
			v = method.invoke(o);
			returnTypeClassName = method.getReturnType().getName();
		}
							
		// check if array
		if (p.isArray()) {
												
			if (v != null) {
				
				SOAPElement arrayElement = parentElement.addChildElement("ArrayOf" + p.getName(), "", NAMESPACE);
				
				for (int i = 0; i < Array.getLength(v); i++) {
					
					SOAPElement childElement = arrayElement.addChildElement(p.getName(), "", NAMESPACE);
					
					Object w = Array.get(v, i);

					if (p.isComplexType()) {
						
						// modify the name to be the enclosed class
						returnTypeClassName = p.getMethodClass().getComponentType().getName();
																														
						SOAPClass soapClass = _soapClasses.get(returnTypeClassName);
						
						for (SOAPClassElement childClassElement : soapClass.getSOAPClassElements()) {
							
							addObjectElement(childElement, childClassElement, w);
							
						}
																		
					} else {
																
						childElement.setTextContent(getXMLValue(p, w));
						
					}
					
				}
				
			}
												
		} else {
														
			if (p.isComplexType()) {
													
				if (v != null) {
					
					SOAPElement childElement = parentElement.addChildElement(p.getName(), "", NAMESPACE);
					
					SOAPClass soapClass = _soapClasses.get(returnTypeClassName);
					
					for (SOAPClassElement childClassElement : soapClass.getSOAPClassElements()) {
						
						addObjectElement(childElement, childClassElement, v);
						
					}
					
				}
														
			} else {
																								
				if (v != null) {
					
					SOAPElement childElement = parentElement.addChildElement(p.getName(), "", NAMESPACE);
					
					childElement.setTextContent(getXMLValue(p, v));
					
				}
										
			}
			
		}
		
	}
	
	private SOAPMessage getSOAPMessage(Object o) 
			throws SOAPException, 
			SecurityException, 
			NoSuchMethodException,
			NoSuchFieldException, 
			IllegalArgumentException, 
			IllegalAccessException, 
			InvocationTargetException {
		
		MessageFactory WSFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
		
		SOAPMessage soapMessage = WSFactory.createMessage();
		
		SOAPEnvelope soapEnvelope = soapMessage.getSOAPPart().getEnvelope();	
		
		soapEnvelope.removeChild(soapMessage.getSOAPHeader());
		
		soapEnvelope.addNamespaceDeclaration(NAMESPACE_PREFIX, NAMESPACE);
		
		SOAPBody soapBody = soapMessage.getSOAPBody();
		
		SOAPElement soapElement = soapBody.addChildElement(o.getClass().getSimpleName(), "", NAMESPACE);
		
		Class responseClass = o.getClass();
						
		if (getIsSimpleSOAPType(responseClass)) {
			
			soapElement.setTextContent(o.toString());
			
		} else {
			
			SOAPClass soapClass = _soapClasses.get(o.getClass().getName());
			
			if (soapClass != null) {
												
				for (SOAPClassElement p : soapClass.getSOAPClassElements()) {
					
					addObjectElement(soapElement, p, o);
														
				}
				
			}
			
		}
		
		return soapMessage;
		
	}
	
	private SOAPMessage getFaultSOAPMessage(String fault, boolean client) throws SOAPException {
		
		MessageFactory WSFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
		
		SOAPMessage soapMessage = WSFactory.createMessage();
		
		SOAPEnvelope soapEnvelope = soapMessage.getSOAPPart().getEnvelope();	
		
		soapEnvelope.addNamespaceDeclaration(NAMESPACE_PREFIX, NAMESPACE);
		
		SOAPBody soapBody = soapMessage.getSOAPBody();
		
		SOAPElement soapFault = soapBody.addChildElement("Fault", "SOAP-ENV");
		
		SOAPElement faultCode = soapBody.addChildElement("faultcode");
		if (client) {
			faultCode.setTextContent("SOAP-ENV:Client");
		} else {
			faultCode.setTextContent("SOAP-ENV:Server");
		}
		
		SOAPElement faultMessage = soapBody.addChildElement("faultstring");
				
		faultMessage.setTextContent(fault);
		
		soapFault.appendChild(faultCode);
		soapFault.appendChild(faultMessage);
		
		return soapMessage;
		
	}
	
	public SOAPMessage getSOAPFault(Exception ex) throws SOAPException {
				
		return getFaultSOAPMessage(ex.getClass().getSimpleName() + " : " + ex.getMessage(), false);
		
	}
	
	public SOAPMessage getSOAPFault(ArrayList<String> faults) throws SOAPException {
						
			String faultString = "";
			
			for (String fault : faults) {
				
				faultString = faultString + fault + "\n";
				
			}
			
			return getFaultSOAPMessage(faultString, true);
		
	}
	
	public SOAPMessage getSOAPResponse(String operationName, SOAPMessage soapRequest, ServletContext servletContext) throws SOAPException {
		
		SOAPMessage response;
	
		try {
			
			if (_logger.isDebugEnabled()) {
												
				_logger.debug("SOAP request:\n" + getXMLString(new DOMSource(soapRequest.getSOAPPart().getEnvelope())));
				
			}
			
									
			SOAPClass s = _operations.get(operationName);
			
			if (s == null) throw new UnrecognizedOperation();
			
		    Document body = soapRequest.getSOAPBody().extractContentAsDocument();  
		    
		    Source bodySource = new DOMSource(body);
		        
		    Validator validator;
		    
		    if (_validators.contains(operationName)) {
		    	
		    	validator = _validators.get(operationName);
		    	
		    } else {
		    	
		    	SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			    
			    Source schemaSource = new DOMSource(getRequestSchema(operationName));

			    StringReader schemaStringReader = new StringReader(getXMLString(schemaSource)); 
		
			    SAXSource schemaSAXSource = new SAXSource(new InputSource(schemaStringReader));
			    
			    Schema schema = schemaFactory.newSchema(schemaSAXSource);
			    
			    validator = schema.newValidator();
			    		        		       		        		    	
		    }
		    
		    ValidationHandler handler = new ValidationHandler();
		    
		    validator.setErrorHandler(handler);
		    
		    validator.validate(bodySource);
		    
		    if (handler.getFailures().size() > 0) {
		    	
		    	response = getSOAPFault(handler.getFailures());
		    			    	
		    } else {
		    	
		    	WSFactory.Request r = (WSFactory.Request) getObject(body.getFirstChild(), s.getBaseClass().getName());
				
				Object o = r.getResponse(servletContext);
				
				response = getSOAPMessage(o);
		    	
		    }
		    		    		    			    			    		    		    	    	    				    
	    } catch (Exception ex) {
	    	
	    	response = getSOAPFault(ex);
	    	
	    	try {	    					    
	    		
	    		Source responseSource = new DOMSource(response.getSOAPPart().getEnvelope());
				
	    		_logger.error("SOAP response:\n" + getXMLString(responseSource), ex);
	    		
	    	} catch (Exception ex2) { 
	    		
	    		_logger.error(ex2);	    	
	    		
	    	}
	    		    		    
	    }
		
		if (_logger.isDebugEnabled()) {
    					
			_logger.debug("SOAP response:\n" + getXMLString(new DOMSource(response.getSOAPPart().getEnvelope())));
			
		}
		
		return response;
		    		    	        		
	}

	private static class ValidationHandler extends DefaultHandler {
	
	    private String _elementName;
	    private ArrayList<String> _failures;

	    public ArrayList<String> getFailures() { return _failures; }
	    
	    public ValidationHandler() {	    	
	    	_elementName = "";
	    	_failures = new ArrayList<String>();	    		    	
	    }
		 
		@Override
        public void warning(SAXParseException exception) throws SAXException {
	    	_failures.add(exception.getMessage());
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
        	_failures.add(exception.getMessage());
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
        	_failures.add(exception.getMessage());
        }
		    	
	}
	
	private Document getWSDLPrivate(String operationName, String endPoint) 
			throws UnrecognizedOperation,
			ParserConfigurationException, 
			SecurityException, 
			NoSuchMethodException, 
			ClassNotFoundException {
		
		SOAPClass sReq = _operations.get(operationName);
		
		if (sReq == null) throw new UnrecognizedOperation();
		
		// may be a smarter way to get the response name at some point
		
		String responseName = null;
		
		Method[] methods = sReq.getBaseClass().getMethods();
		
		for (Method m : methods) {
			if (m.getName().equals("getResponse") && !m.getReturnType().getName().equals("java.lang.Object")) {
				responseName = m.getReturnType().getSimpleName();
				break;
			}
		}
				
		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbfac.newDocumentBuilder();
        DOMImplementation di = db.getDOMImplementation();
        
        Document doc = di.createDocument("http://schemas.xmlsoap.org/wsdl/", "wsdl:definitions", null);
        
        Element root = doc.getDocumentElement();
        root.setAttribute("xmlns:soap", "http://schemas.xmlsoap.org/wsdl/soap/");
        root.setAttribute("xmlns:xsd", NAMESPACE);
        root.setAttribute("xmlns:tns", NAMESPACE + ".wsdl");
        root.setAttribute("targetNamespace", NAMESPACE + ".wsdl");
                
        Element types = doc.createElement("wsdl:types");        
        root.appendChild(types);
                
        Element schema = getWSDLSchema(doc, sReq.getBaseClass());
        types.appendChild(schema);                
        
        Element messageIn = doc.createElement("wsdl:message");
        messageIn.setAttribute("name", "Input");        
        root.appendChild(messageIn);
        
        Element messageInPart = doc.createElement("wsdl:part");
        messageInPart.setAttribute("name", "body");
        messageInPart.setAttribute("element", "xsd:" + sReq.getBaseClass().getSimpleName()); 
        messageIn.appendChild(messageInPart);
        
        Element messageOut = doc.createElement("wsdl:message");
        messageOut.setAttribute("name", "Output");        
        root.appendChild(messageOut);
        
        Element messageOutPart = doc.createElement("wsdl:part");
        messageOutPart.setAttribute("name", "body");
        messageOutPart.setAttribute("element", "xsd:" + responseName); 
        messageOut.appendChild(messageOutPart);
        
        Element portType = doc.createElement("wsdl:portType");
        portType.setAttribute("name", "PortType");
        root.appendChild(portType);
        Element portTypeOperation = doc.createElement("wsdl:operation");
        portType.appendChild(portTypeOperation);
        portTypeOperation.setAttribute("name", operationName); 
        
        Element portTypeOperationIn = doc.createElement("wsdl:input");
        portTypeOperationIn.setAttribute("message", "tns:Input");
        portTypeOperation.appendChild(portTypeOperationIn);
        
        Element portTypeOperationOut = doc.createElement("wsdl:output");
        portTypeOperationOut.setAttribute("message", "tns:Output");
        portTypeOperation.appendChild(portTypeOperationOut);
        
        Element binding = doc.createElement("wsdl:binding");
        binding.setAttribute("name", operationName + "Binding");
        binding.setAttribute("type", "tns:PortType");
        root.appendChild(binding);
        
        Element soapBinding = doc.createElement("soap:binding");
        soapBinding.setAttribute("style", "document");
        soapBinding.setAttribute("transport", "http://schemas.xmlsoap.org/soap/http");
        binding.appendChild(soapBinding);
        
        Element bindingOperation = doc.createElement("wsdl:operation");
        bindingOperation.setAttribute("name", operationName);  
        binding.appendChild(bindingOperation);
        
        Element soapOperation = doc.createElement("soap:operation");
        soapOperation.setAttribute("soapAction", operationName);
        bindingOperation.appendChild(soapOperation);
        
        Element bindingOperationIn = doc.createElement("wsdl:input");
        bindingOperation.appendChild(bindingOperationIn);
        
        Element bindingOperationOut = doc.createElement("wsdl:output");
        bindingOperation.appendChild(bindingOperationOut);
        
        Element soapBodyIn = doc.createElement("soap:body");
        soapBodyIn.setAttribute("use", "literal");
        bindingOperationIn.appendChild(soapBodyIn);
        
        Element soapBodyOut = doc.createElement("soap:body");
        soapBodyOut.setAttribute("use", "literal");        
        bindingOperationOut.appendChild(soapBodyOut);
        
        Element service = doc.createElement("wsdl:service");
        service.setAttribute("name", "Service");
        root.appendChild(service);
        
        Element port = doc.createElement("wsdl:port");
        port.setAttribute("name", "Port");
        port.setAttribute("binding", "tns:" + operationName + "Binding");
        service.appendChild(port);
        
        Element address = doc.createElement("soap:address");
        address.setAttribute("location", endPoint);
        port.appendChild(address);
                
        return doc; 
		
	}
	
	public Document getWSDL(String operationName, String endPoint) 
			throws UnrecognizedOperation,
			ParserConfigurationException, 
			SecurityException, 
			NoSuchMethodException, 
			ClassNotFoundException {
		
		if (_wsdls.contains(operationName)) {
			
			return _wsdls.get(operationName);
			
		} else {
			
			_complexTypes.clear();
			
			Document wsdl = getWSDLPrivate(operationName, endPoint);
			
			return wsdl;
			
		}
					
	}
	
  	public static String getXMLString(Source source) {
		  		  		
  		try {
  			
  			//set up a transformer
  	        TransformerFactory transfac = TransformerFactory.newInstance();
  	        Transformer trans = transfac.newTransformer();
  	        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
  	        trans.setOutputProperty(OutputKeys.INDENT, "yes");
  			        
  	        StringWriter sw = new StringWriter();
  	        StreamResult result = new StreamResult(sw);
  	          
  	        trans.transform(source, result);
  	        
  	        return sw.toString();
  			
  		} catch (Exception ex) {
  			
  			return ex.getMessage();
  			
  		}

	}

}


