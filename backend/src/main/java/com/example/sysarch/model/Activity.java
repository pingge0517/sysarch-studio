/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Activity</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.Activity#getActions <em>Actions</em>}</li>
 *   <li>{@link com.example.sysarch.model.Activity#getFlows <em>Flows</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getActivity()
 * @model
 * @generated
 */
public interface Activity extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Actions</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Action}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Actions</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getActivity_Actions()
	 * @model containment="true"
	 * @generated
	 */
	EList<Action> getActions();

	/**
	 * Returns the value of the '<em><b>Flows</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.ActivityFlow}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Flows</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getActivity_Flows()
	 * @model containment="true"
	 * @generated
	 */
	EList<ActivityFlow> getFlows();

} // Activity
