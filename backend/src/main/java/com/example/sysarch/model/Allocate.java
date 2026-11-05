/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Allocate</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Allocate#getFrom <em>From</em>}</li>
 *   <li>{@link com.example.sysarch.model.Allocate#getTo <em>To</em>}</li>
 *   <li>{@link com.example.sysarch.model.Allocate#getRationale <em>Rationale</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getAllocate()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot LayerAdjacentOnly='(from.layer = Layer::OPERATIONAL and to.layer = Layer::SYSTEM) or (from.layer = Layer::SYSTEM and to.layer = Layer::LOGICAL) or (from.layer = Layer::LOGICAL and to.layer = Layer::PHYSICAL) or (from.layer = Layer::PHYSICAL and to.layer = Layer::EPBS)' NoSelfAllocate='from &lt;&gt; to'"
 * @generated
 */
public interface Allocate extends ArchElement {
	/**
	 * Returns the value of the '<em><b>From</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>From</em>' reference.
	 * @see #setFrom(ArchElement)
	 * @see com.example.sysarch.model.SysarchPackage#getAllocate_From()
	 * @model required="true"
	 * @generated
	 */
	ArchElement getFrom();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Allocate#getFrom <em>From</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>From</em>' reference.
	 * @see #getFrom()
	 * @generated
	 */
	void setFrom(ArchElement value);

	/**
	 * Returns the value of the '<em><b>To</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>To</em>' reference.
	 * @see #setTo(ArchElement)
	 * @see com.example.sysarch.model.SysarchPackage#getAllocate_To()
	 * @model required="true"
	 * @generated
	 */
	ArchElement getTo();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Allocate#getTo <em>To</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>To</em>' reference.
	 * @see #getTo()
	 * @generated
	 */
	void setTo(ArchElement value);

	/**
	 * Returns the value of the '<em><b>Rationale</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Rationale</em>' attribute.
	 * @see #setRationale(String)
	 * @see com.example.sysarch.model.SysarchPackage#getAllocate_Rationale()
	 * @model
	 * @generated
	 */
	String getRationale();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Allocate#getRationale <em>Rationale</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Rationale</em>' attribute.
	 * @see #getRationale()
	 * @generated
	 */
	void setRationale(String value);

} // Allocate
