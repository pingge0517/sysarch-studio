/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Diagram Layout</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getDiagramKey <em>Diagram Key</em>}</li>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getElementRef <em>Element Ref</em>}</li>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getX <em>X</em>}</li>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getY <em>Y</em>}</li>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getWidth <em>Width</em>}</li>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getHeight <em>Height</em>}</li>
 *   <li>{@link com.example.sysarch.model.DiagramLayout#getWaypoints <em>Waypoints</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout()
 * @model
 * @generated
 */
public interface DiagramLayout extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Diagram Key</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Diagram Key</em>' attribute.
	 * @see #setDiagramKey(String)
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_DiagramKey()
	 * @model
	 * @generated
	 */
	String getDiagramKey();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.DiagramLayout#getDiagramKey <em>Diagram Key</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Diagram Key</em>' attribute.
	 * @see #getDiagramKey()
	 * @generated
	 */
	void setDiagramKey(String value);

	/**
	 * Returns the value of the '<em><b>Element Ref</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Element Ref</em>' attribute.
	 * @see #setElementRef(String)
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_ElementRef()
	 * @model
	 * @generated
	 */
	String getElementRef();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.DiagramLayout#getElementRef <em>Element Ref</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Element Ref</em>' attribute.
	 * @see #getElementRef()
	 * @generated
	 */
	void setElementRef(String value);

	/**
	 * Returns the value of the '<em><b>X</b></em>' attribute.
	 * The default value is <code>"0"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>X</em>' attribute.
	 * @see #setX(int)
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_X()
	 * @model default="0"
	 * @generated
	 */
	int getX();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.DiagramLayout#getX <em>X</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>X</em>' attribute.
	 * @see #getX()
	 * @generated
	 */
	void setX(int value);

	/**
	 * Returns the value of the '<em><b>Y</b></em>' attribute.
	 * The default value is <code>"0"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Y</em>' attribute.
	 * @see #setY(int)
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_Y()
	 * @model default="0"
	 * @generated
	 */
	int getY();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.DiagramLayout#getY <em>Y</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Y</em>' attribute.
	 * @see #getY()
	 * @generated
	 */
	void setY(int value);

	/**
	 * Returns the value of the '<em><b>Width</b></em>' attribute.
	 * The default value is <code>"180"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Width</em>' attribute.
	 * @see #setWidth(int)
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_Width()
	 * @model default="180"
	 * @generated
	 */
	int getWidth();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.DiagramLayout#getWidth <em>Width</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Width</em>' attribute.
	 * @see #getWidth()
	 * @generated
	 */
	void setWidth(int value);

	/**
	 * Returns the value of the '<em><b>Height</b></em>' attribute.
	 * The default value is <code>"90"</code>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Height</em>' attribute.
	 * @see #setHeight(int)
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_Height()
	 * @model default="90"
	 * @generated
	 */
	int getHeight();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.DiagramLayout#getHeight <em>Height</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Height</em>' attribute.
	 * @see #getHeight()
	 * @generated
	 */
	void setHeight(int value);

	/**
	 * Returns the value of the '<em><b>Waypoints</b></em>' attribute list.
	 * The list contents are of type {@link java.lang.String}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Waypoints</em>' attribute list.
	 * @see com.example.sysarch.model.SysarchPackage#getDiagramLayout_Waypoints()
	 * @model
	 * @generated
	 */
	EList<String> getWaypoints();

} // DiagramLayout
