/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Connection</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Connection#getSource <em>Source</em>}</li>
 *   <li>{@link com.example.sysarch.model.Connection#getTarget <em>Target</em>}</li>
 *   <li>{@link com.example.sysarch.model.Connection#getKind <em>Kind</em>}</li>
 *   <li>{@link com.example.sysarch.model.Connection#getInterfaceType <em>Interface Type</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getConnection()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot PortInterfaceAssignable='kind = ConnectionKind::ASSEMBLY implies (not source.interfaceType.oclIsUndefined() and not target.interfaceType.oclIsUndefined() and source.interfaceType.compatibleWith(target.interfaceType))' NoSelfLoop='source &lt;&gt; target' DirectionCompatible='kind = ConnectionKind::ASSEMBLY implies (source.direction &lt;&gt; target.direction or source.direction = PortDirection::INOUT or target.direction = PortDirection::INOUT)'"
 * @generated
 */
public interface Connection extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Source</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Source</em>' reference.
	 * @see #setSource(Port)
	 * @see com.example.sysarch.model.SysarchPackage#getConnection_Source()
	 * @model required="true"
	 * @generated
	 */
	Port getSource();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Connection#getSource <em>Source</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Source</em>' reference.
	 * @see #getSource()
	 * @generated
	 */
	void setSource(Port value);

	/**
	 * Returns the value of the '<em><b>Target</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Target</em>' reference.
	 * @see #setTarget(Port)
	 * @see com.example.sysarch.model.SysarchPackage#getConnection_Target()
	 * @model required="true"
	 * @generated
	 */
	Port getTarget();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Connection#getTarget <em>Target</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Target</em>' reference.
	 * @see #getTarget()
	 * @generated
	 */
	void setTarget(Port value);

	/**
	 * Returns the value of the '<em><b>Kind</b></em>' attribute.
	 * The default value is <code>"ASSEMBLY"</code>.
	 * The literals are from the enumeration {@link com.example.sysarch.model.ConnectionKind}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Kind</em>' attribute.
	 * @see com.example.sysarch.model.ConnectionKind
	 * @see #setKind(ConnectionKind)
	 * @see com.example.sysarch.model.SysarchPackage#getConnection_Kind()
	 * @model default="ASSEMBLY"
	 * @generated
	 */
	ConnectionKind getKind();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Connection#getKind <em>Kind</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Kind</em>' attribute.
	 * @see com.example.sysarch.model.ConnectionKind
	 * @see #getKind()
	 * @generated
	 */
	void setKind(ConnectionKind value);

	/**
	 * Returns the value of the '<em><b>Interface Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Interface Type</em>' attribute.
	 * @see #setInterfaceType(String)
	 * @see com.example.sysarch.model.SysarchPackage#getConnection_InterfaceType()
	 * @model
	 * @generated
	 */
	String getInterfaceType();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Connection#getInterfaceType <em>Interface Type</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Interface Type</em>' attribute.
	 * @see #getInterfaceType()
	 * @generated
	 */
	void setInterfaceType(String value);

} // Connection
