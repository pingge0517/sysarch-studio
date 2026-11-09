/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Interface Def</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.InterfaceDef#getProtocol <em>Protocol</em>}</li>
 *   <li>{@link com.example.sysarch.model.InterfaceDef#getDataType <em>Data Type</em>}</li>
 *   <li>{@link com.example.sysarch.model.InterfaceDef#getSuperTypes <em>Super Types</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getInterfaceDef()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot ProtocolRequired='not protocol.oclIsUndefined() and protocol.size() &gt; 0'"
 * @generated
 */
public interface InterfaceDef extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Protocol</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Protocol</em>' attribute.
	 * @see #setProtocol(String)
	 * @see com.example.sysarch.model.SysarchPackage#getInterfaceDef_Protocol()
	 * @model
	 * @generated
	 */
	String getProtocol();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.InterfaceDef#getProtocol <em>Protocol</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Protocol</em>' attribute.
	 * @see #getProtocol()
	 * @generated
	 */
	void setProtocol(String value);

	/**
	 * Returns the value of the '<em><b>Data Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Data Type</em>' attribute.
	 * @see #setDataType(String)
	 * @see com.example.sysarch.model.SysarchPackage#getInterfaceDef_DataType()
	 * @model
	 * @generated
	 */
	String getDataType();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.InterfaceDef#getDataType <em>Data Type</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Data Type</em>' attribute.
	 * @see #getDataType()
	 * @generated
	 */
	void setDataType(String value);

	/**
	 * Returns the value of the '<em><b>Super Types</b></em>' reference list.
	 * The list contents are of type {@link com.example.sysarch.model.InterfaceDef}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Super Types</em>' reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getInterfaceDef_SuperTypes()
	 * @model
	 * @generated
	 */
	EList<InterfaceDef> getSuperTypes();

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * P1 T2: the same entity, or one an ancestor of the other along superTypes (transitively). Implemented in Java with a cache (spec P1 §2.3); OCL calls it.
	 * <!-- end-model-doc -->
	 * @model
	 * @generated
	 */
	boolean compatibleWith(InterfaceDef other);

} // InterfaceDef
