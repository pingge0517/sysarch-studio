/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Interaction</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Interaction#getLifelines <em>Lifelines</em>}</li>
 *   <li>{@link com.example.sysarch.model.Interaction#getMessages <em>Messages</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getInteraction()
 * @model
 * @generated
 */
public interface Interaction extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Lifelines</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Lifeline}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Lifelines</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getInteraction_Lifelines()
	 * @model containment="true"
	 * @generated
	 */
	EList<Lifeline> getLifelines();

	/**
	 * Returns the value of the '<em><b>Messages</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Message}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Messages</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getInteraction_Messages()
	 * @model containment="true"
	 * @generated
	 */
	EList<Message> getMessages();

} // Interaction
