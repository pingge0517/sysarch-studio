/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Port</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Port#getOwner <em>Owner</em>}</li>
 *   <li>{@link com.example.sysarch.model.Port#getPartOwner <em>Part Owner</em>}</li>
 *   <li>{@link com.example.sysarch.model.Port#getDirection <em>Direction</em>}</li>
 *   <li>{@link com.example.sysarch.model.Port#getInterfaceType <em>Interface Type</em>}</li>
 *   <li>{@link com.example.sysarch.model.Port#isIsConjugated <em>Is Conjugated</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getPort()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot InterfaceTypeRequired='direction = PortDirection::INOUT or (not interfaceType.oclIsUndefined() and interfaceType.size() &gt; 0)'"
 * @generated
 */
public interface Port extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Owner</b></em>' container reference.
	 * It is bidirectional and its opposite is '{@link com.example.sysarch.model.PartDefinition#getOwnedPorts <em>Owned Ports</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Owner</em>' container reference.
	 * @see #setOwner(PartDefinition)
	 * @see com.example.sysarch.model.SysarchPackage#getPort_Owner()
	 * @see com.example.sysarch.model.PartDefinition#getOwnedPorts
	 * @model opposite="ownedPorts" transient="false"
	 * @generated
	 */
	PartDefinition getOwner();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Port#getOwner <em>Owner</em>}' container reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Owner</em>' container reference.
	 * @see #getOwner()
	 * @generated
	 */
	void setOwner(PartDefinition value);

	/**
	 * Returns the value of the '<em><b>Part Owner</b></em>' container reference.
	 * It is bidirectional and its opposite is '{@link com.example.sysarch.model.Part#getPorts <em>Ports</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Part Owner</em>' container reference.
	 * @see #setPartOwner(Part)
	 * @see com.example.sysarch.model.SysarchPackage#getPort_PartOwner()
	 * @see com.example.sysarch.model.Part#getPorts
	 * @model opposite="ports" transient="false"
	 * @generated
	 */
	Part getPartOwner();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Port#getPartOwner <em>Part Owner</em>}' container reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Part Owner</em>' container reference.
	 * @see #getPartOwner()
	 * @generated
	 */
	void setPartOwner(Part value);

	/**
	 * Returns the value of the '<em><b>Direction</b></em>' attribute.
	 * The default value is <code>"INOUT"</code>.
	 * The literals are from the enumeration {@link com.example.sysarch.model.PortDirection}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Direction</em>' attribute.
	 * @see com.example.sysarch.model.PortDirection
	 * @see #setDirection(PortDirection)
	 * @see com.example.sysarch.model.SysarchPackage#getPort_Direction()
	 * @model default="INOUT"
	 * @generated
	 */
	PortDirection getDirection();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Port#getDirection <em>Direction</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Direction</em>' attribute.
	 * @see com.example.sysarch.model.PortDirection
	 * @see #getDirection()
	 * @generated
	 */
	void setDirection(PortDirection value);

	/**
	 * Returns the value of the '<em><b>Interface Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Interface Type</em>' attribute.
	 * @see #setInterfaceType(String)
	 * @see com.example.sysarch.model.SysarchPackage#getPort_InterfaceType()
	 * @model
	 * @generated
	 */
	String getInterfaceType();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Port#getInterfaceType <em>Interface Type</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Interface Type</em>' attribute.
	 * @see #getInterfaceType()
	 * @generated
	 */
	void setInterfaceType(String value);

	/**
	 * Returns the value of the '<em><b>Is Conjugated</b></em>' attribute.
	 * The default value is <code>"false"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Is Conjugated</em>' attribute.
	 * @see #setIsConjugated(boolean)
	 * @see com.example.sysarch.model.SysarchPackage#getPort_IsConjugated()
	 * @model default="false"
	 * @generated
	 */
	boolean isIsConjugated();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Port#isIsConjugated <em>Is Conjugated</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Is Conjugated</em>' attribute.
	 * @see #isIsConjugated()
	 * @generated
	 */
	void setIsConjugated(boolean value);

} // Port
