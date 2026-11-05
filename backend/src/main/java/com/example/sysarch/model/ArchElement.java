/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Arch Element</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.ArchElement#getId <em>Id</em>}</li>
 *   <li>{@link com.example.sysarch.model.ArchElement#getName <em>Name</em>}</li>
 *   <li>{@link com.example.sysarch.model.ArchElement#getDescription <em>Description</em>}</li>
 *   <li>{@link com.example.sysarch.model.ArchElement#getLayer <em>Layer</em>}</li>
 *   <li>{@link com.example.sysarch.model.ArchElement#getStereotypes <em>Stereotypes</em>}</li>
 *   <li>{@link com.example.sysarch.model.ArchElement#getWorkItemId <em>Work Item Id</em>}</li>
 *   <li>{@link com.example.sysarch.model.ArchElement#getRev <em>Rev</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getArchElement()
 * @model abstract="true"
 *        annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot NameNotEmpty='not name.oclIsUndefined() and name.size() &gt; 0'"
 * @generated
 */
public interface ArchElement extends EObject {
	/**
	 * Returns the value of the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Id</em>' attribute.
	 * @see #setId(String)
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_Id()
	 * @model id="true"
	 * @generated
	 */
	String getId();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ArchElement#getId <em>Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Id</em>' attribute.
	 * @see #getId()
	 * @generated
	 */
	void setId(String value);

	/**
	 * Returns the value of the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Name</em>' attribute.
	 * @see #setName(String)
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_Name()
	 * @model
	 * @generated
	 */
	String getName();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ArchElement#getName <em>Name</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Name</em>' attribute.
	 * @see #getName()
	 * @generated
	 */
	void setName(String value);

	/**
	 * Returns the value of the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Description</em>' attribute.
	 * @see #setDescription(String)
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_Description()
	 * @model
	 * @generated
	 */
	String getDescription();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ArchElement#getDescription <em>Description</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Description</em>' attribute.
	 * @see #getDescription()
	 * @generated
	 */
	void setDescription(String value);

	/**
	 * Returns the value of the '<em><b>Layer</b></em>' attribute.
	 * The default value is <code>"LOGICAL"</code>.
	 * The literals are from the enumeration {@link com.example.sysarch.model.Layer}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Layer</em>' attribute.
	 * @see com.example.sysarch.model.Layer
	 * @see #setLayer(Layer)
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_Layer()
	 * @model default="LOGICAL"
	 * @generated
	 */
	Layer getLayer();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ArchElement#getLayer <em>Layer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Layer</em>' attribute.
	 * @see com.example.sysarch.model.Layer
	 * @see #getLayer()
	 * @generated
	 */
	void setLayer(Layer value);

	/**
	 * Returns the value of the '<em><b>Stereotypes</b></em>' attribute list.
	 * The list contents are of type {@link java.lang.String}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Stereotypes</em>' attribute list.
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_Stereotypes()
	 * @model
	 * @generated
	 */
	EList<String> getStereotypes();

	/**
	 * Returns the value of the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Work Item Id</em>' attribute.
	 * @see #setWorkItemId(String)
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_WorkItemId()
	 * @model
	 * @generated
	 */
	String getWorkItemId();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ArchElement#getWorkItemId <em>Work Item Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Work Item Id</em>' attribute.
	 * @see #getWorkItemId()
	 * @generated
	 */
	void setWorkItemId(String value);

	/**
	 * Returns the value of the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Rev</em>' attribute.
	 * @see #setRev(int)
	 * @see com.example.sysarch.model.SysarchPackage#getArchElement_Rev()
	 * @model
	 * @generated
	 */
	int getRev();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ArchElement#getRev <em>Rev</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Rev</em>' attribute.
	 * @see #getRev()
	 * @generated
	 */
	void setRev(int value);

} // ArchElement
