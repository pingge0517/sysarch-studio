/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.SysarchPackage;

import java.util.Collection;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EClass;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

import org.eclipse.emf.ecore.util.EDataTypeUniqueEList;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Diagram Layout</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getDiagramKey <em>Diagram Key</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getElementRef <em>Element Ref</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getX <em>X</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getY <em>Y</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getWidth <em>Width</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getHeight <em>Height</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.DiagramLayoutImpl#getWaypoints <em>Waypoints</em>}</li>
 * </ul>
 *
 * @generated
 */
public class DiagramLayoutImpl extends ArchElementImpl implements DiagramLayout {
	/**
	 * The default value of the '{@link #getDiagramKey() <em>Diagram Key</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDiagramKey()
	 * @generated
	 * @ordered
	 */
	protected static final String DIAGRAM_KEY_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getDiagramKey() <em>Diagram Key</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDiagramKey()
	 * @generated
	 * @ordered
	 */
	protected String diagramKey = DIAGRAM_KEY_EDEFAULT;

	/**
	 * The default value of the '{@link #getElementRef() <em>Element Ref</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getElementRef()
	 * @generated
	 * @ordered
	 */
	protected static final String ELEMENT_REF_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getElementRef() <em>Element Ref</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getElementRef()
	 * @generated
	 * @ordered
	 */
	protected String elementRef = ELEMENT_REF_EDEFAULT;

	/**
	 * The default value of the '{@link #getX() <em>X</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getX()
	 * @generated
	 * @ordered
	 */
	protected static final int X_EDEFAULT = 0;

	/**
	 * The cached value of the '{@link #getX() <em>X</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getX()
	 * @generated
	 * @ordered
	 */
	protected int x = X_EDEFAULT;

	/**
	 * The default value of the '{@link #getY() <em>Y</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getY()
	 * @generated
	 * @ordered
	 */
	protected static final int Y_EDEFAULT = 0;

	/**
	 * The cached value of the '{@link #getY() <em>Y</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getY()
	 * @generated
	 * @ordered
	 */
	protected int y = Y_EDEFAULT;

	/**
	 * The default value of the '{@link #getWidth() <em>Width</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWidth()
	 * @generated
	 * @ordered
	 */
	protected static final int WIDTH_EDEFAULT = 180;

	/**
	 * The cached value of the '{@link #getWidth() <em>Width</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWidth()
	 * @generated
	 * @ordered
	 */
	protected int width = WIDTH_EDEFAULT;

	/**
	 * The default value of the '{@link #getHeight() <em>Height</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getHeight()
	 * @generated
	 * @ordered
	 */
	protected static final int HEIGHT_EDEFAULT = 90;

	/**
	 * The cached value of the '{@link #getHeight() <em>Height</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getHeight()
	 * @generated
	 * @ordered
	 */
	protected int height = HEIGHT_EDEFAULT;

	/**
	 * The cached value of the '{@link #getWaypoints() <em>Waypoints</em>}' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWaypoints()
	 * @generated
	 * @ordered
	 */
	protected EList<String> waypoints;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected DiagramLayoutImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.DIAGRAM_LAYOUT;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getDiagramKey() {
		return diagramKey;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setDiagramKey(String newDiagramKey) {
		String oldDiagramKey = diagramKey;
		diagramKey = newDiagramKey;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.DIAGRAM_LAYOUT__DIAGRAM_KEY, oldDiagramKey, diagramKey));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getElementRef() {
		return elementRef;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setElementRef(String newElementRef) {
		String oldElementRef = elementRef;
		elementRef = newElementRef;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.DIAGRAM_LAYOUT__ELEMENT_REF, oldElementRef, elementRef));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getX() {
		return x;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setX(int newX) {
		int oldX = x;
		x = newX;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.DIAGRAM_LAYOUT__X, oldX, x));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getY() {
		return y;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setY(int newY) {
		int oldY = y;
		y = newY;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.DIAGRAM_LAYOUT__Y, oldY, y));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getWidth() {
		return width;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setWidth(int newWidth) {
		int oldWidth = width;
		width = newWidth;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.DIAGRAM_LAYOUT__WIDTH, oldWidth, width));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getHeight() {
		return height;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setHeight(int newHeight) {
		int oldHeight = height;
		height = newHeight;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.DIAGRAM_LAYOUT__HEIGHT, oldHeight, height));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EList<String> getWaypoints() {
		if (waypoints == null) {
			waypoints = new EDataTypeUniqueEList<String>(String.class, this, SysarchPackage.DIAGRAM_LAYOUT__WAYPOINTS);
		}
		return waypoints;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.DIAGRAM_LAYOUT__DIAGRAM_KEY:
				return getDiagramKey();
			case SysarchPackage.DIAGRAM_LAYOUT__ELEMENT_REF:
				return getElementRef();
			case SysarchPackage.DIAGRAM_LAYOUT__X:
				return getX();
			case SysarchPackage.DIAGRAM_LAYOUT__Y:
				return getY();
			case SysarchPackage.DIAGRAM_LAYOUT__WIDTH:
				return getWidth();
			case SysarchPackage.DIAGRAM_LAYOUT__HEIGHT:
				return getHeight();
			case SysarchPackage.DIAGRAM_LAYOUT__WAYPOINTS:
				return getWaypoints();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case SysarchPackage.DIAGRAM_LAYOUT__DIAGRAM_KEY:
				setDiagramKey((String)newValue);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__ELEMENT_REF:
				setElementRef((String)newValue);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__X:
				setX((Integer)newValue);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__Y:
				setY((Integer)newValue);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__WIDTH:
				setWidth((Integer)newValue);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__HEIGHT:
				setHeight((Integer)newValue);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__WAYPOINTS:
				getWaypoints().clear();
				getWaypoints().addAll((Collection<? extends String>)newValue);
				return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
			case SysarchPackage.DIAGRAM_LAYOUT__DIAGRAM_KEY:
				setDiagramKey(DIAGRAM_KEY_EDEFAULT);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__ELEMENT_REF:
				setElementRef(ELEMENT_REF_EDEFAULT);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__X:
				setX(X_EDEFAULT);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__Y:
				setY(Y_EDEFAULT);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__WIDTH:
				setWidth(WIDTH_EDEFAULT);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__HEIGHT:
				setHeight(HEIGHT_EDEFAULT);
				return;
			case SysarchPackage.DIAGRAM_LAYOUT__WAYPOINTS:
				getWaypoints().clear();
				return;
		}
		super.eUnset(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
			case SysarchPackage.DIAGRAM_LAYOUT__DIAGRAM_KEY:
				return DIAGRAM_KEY_EDEFAULT == null ? diagramKey != null : !DIAGRAM_KEY_EDEFAULT.equals(diagramKey);
			case SysarchPackage.DIAGRAM_LAYOUT__ELEMENT_REF:
				return ELEMENT_REF_EDEFAULT == null ? elementRef != null : !ELEMENT_REF_EDEFAULT.equals(elementRef);
			case SysarchPackage.DIAGRAM_LAYOUT__X:
				return x != X_EDEFAULT;
			case SysarchPackage.DIAGRAM_LAYOUT__Y:
				return y != Y_EDEFAULT;
			case SysarchPackage.DIAGRAM_LAYOUT__WIDTH:
				return width != WIDTH_EDEFAULT;
			case SysarchPackage.DIAGRAM_LAYOUT__HEIGHT:
				return height != HEIGHT_EDEFAULT;
			case SysarchPackage.DIAGRAM_LAYOUT__WAYPOINTS:
				return waypoints != null && !waypoints.isEmpty();
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (diagramKey: ");
		result.append(diagramKey);
		result.append(", elementRef: ");
		result.append(elementRef);
		result.append(", x: ");
		result.append(x);
		result.append(", y: ");
		result.append(y);
		result.append(", width: ");
		result.append(width);
		result.append(", height: ");
		result.append(height);
		result.append(", waypoints: ");
		result.append(waypoints);
		result.append(')');
		return result.toString();
	}

} //DiagramLayoutImpl
