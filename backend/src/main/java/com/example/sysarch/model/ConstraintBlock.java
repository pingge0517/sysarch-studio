/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.common.util.EList;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Constraint Block</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.ConstraintBlock#getParameters <em>Parameters</em>}</li>
 *   <li>{@link com.example.sysarch.model.ConstraintBlock#getExpression <em>Expression</em>}</li>
 * </ul>
 *
 * @see com.example.sysarch.model.SysarchPackage#getConstraintBlock()
 * @model annotation="http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot HasExpression='not expression.oclIsUndefined()' ParamsHaveUnit='parameters-&gt;forAll(p | not p.unit.oclIsUndefined() and p.unit.size() &gt; 0)' NoDuplicateParamName='parameters-&gt;isUnique(p | p.name)'"
 * @generated
 */
public interface ConstraintBlock extends ArchElement {
	/**
	 * Returns the value of the '<em><b>Parameters</b></em>' containment reference list.
	 * The list contents are of type {@link com.example.sysarch.model.Parameter}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Parameters</em>' containment reference list.
	 * @see com.example.sysarch.model.SysarchPackage#getConstraintBlock_Parameters()
	 * @model containment="true" required="true"
	 * @generated
	 */
	EList<Parameter> getParameters();

	/**
	 * Returns the value of the '<em><b>Expression</b></em>' containment reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Expression</em>' containment reference.
	 * @see #setExpression(ConstraintExpr)
	 * @see com.example.sysarch.model.SysarchPackage#getConstraintBlock_Expression()
	 * @model containment="true" required="true"
	 * @generated
	 */
	ConstraintExpr getExpression();

	/**
	 * Sets the value of the '{@link com.example.sysarch.model.ConstraintBlock#getExpression <em>Expression</em>}' containment reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Expression</em>' containment reference.
	 * @see #getExpression()
	 * @generated
	 */
	void setExpression(ConstraintExpr value);

} // ConstraintBlock
