/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Part Definition</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.PartDefinition#getOwnedParts <em>Owned Parts</em>}</li>
 *   <li>{@link com.example.sysarch.model.PartDefinition#getOwnedPorts <em>Owned Ports</em>}</li>
 *   <li>{@link com.example.sysarch.model.PartDefinition#getSuperTypes <em>Super Types</em>}</li>
 *   <li>{@link com.example.sysarch.model.PartDefinition#isIsAbstract <em>Is Abstract</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getPartDefinition()
 * @model
 * @generated
 */
public interface PartDefinition extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Owned Parts</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Part}.
	 * It is bidirectional and its opposite is '{@link com.example.sysarch.model.Part#getOwner <em>Owner</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Owned Parts</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getPartDefinition_OwnedParts()
	 * @see com.example.sysarch.model.Part#getOwner
	 * @model opposite="owner" containment="true"
	 * @generated
	 */
	EList<Part> getOwnedParts();

	/**
	 * Returns the value of the '<em><b>Owned Ports</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Port}.
	 * It is bidirectional and its opposite is '{@link com.example.sysarch.model.Port#getOwner <em>Owner</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Owned Ports</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getPartDefinition_OwnedPorts()
	 * @see com.example.sysarch.model.Port#getOwner
	 * @model opposite="owner" containment="true"
	 * @generated
	 */
	EList<Port> getOwnedPorts();

	/**
	 * Returns the value of the '<em><b>Super Types</b></em>' reference list.
	 * The list contents are of type {@link com.example.sysarch.model.PartDefinition}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Super Types</em>' reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getPartDefinition_SuperTypes()
	 * @model
	 * @generated
	 */
	EList<PartDefinition> getSuperTypes();

	/**
	 * Returns the value of the '<em><b>Is Abstract</b></em>' attribute.
	 * The default value is <code>"false"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Is Abstract</em>' attribute.
	 * @see #setIsAbstract(boolean)
	 * @see com.example.sysarch.model.SysarchPackage#getPartDefinition_IsAbstract()
	 * @model default="false"
	 * @generated
	 */
	boolean isIsAbstract();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.PartDefinition#isIsAbstract <em>Is Abstract</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Is Abstract</em>' attribute.
	 * @see #isIsAbstract()
	 * @generated
	 */
	void setIsAbstract(boolean value);

} // PartDefinition
