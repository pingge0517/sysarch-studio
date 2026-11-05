/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Part</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Part#getOwner <em>Owner</em>}</li>
 *   <li>{@link com.example.sysarch.model.Part#getTypedBy <em>Typed By</em>}</li>
 *   <li>{@link com.example.sysarch.model.Part#getPorts <em>Ports</em>}</li>
 *   <li>{@link com.example.sysarch.model.Part#getMultiplicity <em>Multiplicity</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getPart()
 * @model
 * @generated
 */
public interface Part extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Owner</b></em>' container reference.
	 * It is bidirectional and its opposite is '{@link com.example.sysarch.model.PartDefinition#getOwnedParts <em>Owned Parts</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Owner</em>' container reference.
	 * @see #setOwner(PartDefinition)
	 * @see com.example.sysarch.model.SysarchPackage#getPart_Owner()
	 * @see com.example.sysarch.model.PartDefinition#getOwnedParts
	 * @model opposite="ownedParts" transient="false"
	 * @generated
	 */
	PartDefinition getOwner();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Part#getOwner <em>Owner</em>}' container reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Owner</em>' container reference.
	 * @see #getOwner()
	 * @generated
	 */
	void setOwner(PartDefinition value);

	/**
	 * Returns the value of the '<em><b>Typed By</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Typed By</em>' reference.
	 * @see #setTypedBy(PartDefinition)
	 * @see com.example.sysarch.model.SysarchPackage#getPart_TypedBy()
	 * @model required="true"
	 * @generated
	 */
	PartDefinition getTypedBy();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Part#getTypedBy <em>Typed By</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Typed By</em>' reference.
	 * @see #getTypedBy()
	 * @generated
	 */
	void setTypedBy(PartDefinition value);

	/**
	 * Returns the value of the '<em><b>Ports</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Port}.
	 * It is bidirectional and its opposite is '{@link com.example.sysarch.model.Port#getPartOwner <em>Part Owner</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Ports</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getPart_Ports()
	 * @see com.example.sysarch.model.Port#getPartOwner
	 * @model opposite="partOwner" containment="true"
	 * @generated
	 */
	EList<Port> getPorts();

	/**
	 * Returns the value of the '<em><b>Multiplicity</b></em>' attribute.
	 * The default value is <code>"1"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Multiplicity</em>' attribute.
	 * @see #setMultiplicity(String)
	 * @see com.example.sysarch.model.SysarchPackage#getPart_Multiplicity()
	 * @model default="1"
	 * @generated
	 */
	String getMultiplicity();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Part#getMultiplicity <em>Multiplicity</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Multiplicity</em>' attribute.
	 * @see #getMultiplicity()
	 * @generated
	 */
	void setMultiplicity(String value);

} // Part
