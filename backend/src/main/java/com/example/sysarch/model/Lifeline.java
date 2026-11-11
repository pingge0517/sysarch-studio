/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Lifeline</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Lifeline#getRepresents <em>Represents</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getLifeline()
 * @model
 * @generated
 */
public interface Lifeline extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Represents</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Represents</em>' reference.
	 * @see #setRepresents(ArchElement)
	 * @see com.example.sysarch.model.SysarchPackage#getLifeline_Represents()
	 * @model
	 * @generated
	 */
	ArchElement getRepresents();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Lifeline#getRepresents <em>Represents</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Represents</em>' reference.
	 * @see #getRepresents()
	 * @generated
	 */
	void setRepresents(ArchElement value);

} // Lifeline
