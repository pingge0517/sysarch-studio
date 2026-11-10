/**
 */
package com.example.sysarch.model;


/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Transition</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Transition#getSource <em>Source</em>}</li>
 *   <li>{@link com.example.sysarch.model.Transition#getTarget <em>Target</em>}</li>
 *   <li>{@link com.example.sysarch.model.Transition#getTrigger <em>Trigger</em>}</li>
 *   <li>{@link com.example.sysarch.model.Transition#getGuard <em>Guard</em>}</li>
 *   <li>{@link com.example.sysarch.model.Transition#getEffect <em>Effect</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getTransition()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot GuardNotEmpty='guard.oclIsUndefined() or guard.size() = 0 or (not trigger.oclIsUndefined() and trigger.size() &gt; 0)'"
 * @generated
 */
public interface Transition extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Source</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Source</em>' reference.
	 * @see #setSource(State)
	 * @see com.example.sysarch.model.SysarchPackage#getTransition_Source()
	 * @model required="true"
	 * @generated
	 */
	State getSource();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Transition#getSource <em>Source</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Source</em>' reference.
	 * @see #getSource()
	 * @generated
	 */
	void setSource(State value);

	/**
	 * Returns the value of the '<em><b>Target</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Target</em>' reference.
	 * @see #setTarget(State)
	 * @see com.example.sysarch.model.SysarchPackage#getTransition_Target()
	 * @model required="true"
	 * @generated
	 */
	State getTarget();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Transition#getTarget <em>Target</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Target</em>' reference.
	 * @see #getTarget()
	 * @generated
	 */
	void setTarget(State value);

	/**
	 * Returns the value of the '<em><b>Trigger</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Trigger</em>' attribute.
	 * @see #setTrigger(String)
	 * @see com.example.sysarch.model.SysarchPackage#getTransition_Trigger()
	 * @model
	 * @generated
	 */
	String getTrigger();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Transition#getTrigger <em>Trigger</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Trigger</em>' attribute.
	 * @see #getTrigger()
	 * @generated
	 */
	void setTrigger(String value);

	/**
	 * Returns the value of the '<em><b>Guard</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Guard</em>' attribute.
	 * @see #setGuard(String)
	 * @see com.example.sysarch.model.SysarchPackage#getTransition_Guard()
	 * @model
	 * @generated
	 */
	String getGuard();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Transition#getGuard <em>Guard</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Guard</em>' attribute.
	 * @see #getGuard()
	 * @generated
	 */
	void setGuard(String value);

	/**
	 * Returns the value of the '<em><b>Effect</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Effect</em>' attribute.
	 * @see #setEffect(String)
	 * @see com.example.sysarch.model.SysarchPackage#getTransition_Effect()
	 * @model
	 * @generated
	 */
	String getEffect();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.Transition#getEffect <em>Effect</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Effect</em>' attribute.
	 * @see #getEffect()
	 * @generated
	 */
	void setEffect(String value);

} // Transition
