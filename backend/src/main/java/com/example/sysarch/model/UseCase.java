/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Use Case</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.UseCase#getActors <em>Actors</em>}</li>
 *   <li>{@link com.example.sysarch.model.UseCase#getSubject <em>Subject</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getUseCase()
 * @model
 * @generated
 */
public interface UseCase extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Actors</b></em>' reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Actor}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Actors</em>' reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getUseCase_Actors()
	 * @model
	 * @generated
	 */
	EList<Actor> getActors();

	/**
	 * Returns the value of the '<em><b>Subject</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Subject</em>' reference.
	 * @see #setSubject(PartDefinition)
	 * @see com.example.sysarch.model.SysarchPackage#getUseCase_Subject()
	 * @model
	 * @generated
	 */
	PartDefinition getSubject();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.UseCase#getSubject <em>Subject</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Subject</em>' reference.
	 * @see #getSubject()
	 * @generated
	 */
	void setSubject(PartDefinition value);

} // UseCase
