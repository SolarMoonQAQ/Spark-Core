/*************************************************************************
 *                                                                       *
 * Open Dynamics Engine, Copyright (C) 2001,2002 Russell L. Smith.       *
 * All rights reserved.  Email: russ@q12.org   Web: www.q12.org          *
 * Open Dynamics Engine 4J, Copyright (C) 2009-2014 Tilmann Zaeschke     *
 * All rights reserved.  Email: ode4j@gmx.de   Web: www.ode4j.org        *
 *                                                                       *
 * This library is free software; you can redistribute it and/or         *
 * modify it under the terms of EITHER:                                  *
 *   (1) The GNU Lesser General Public License as published by the Free  *
 *       Software Foundation; either version 2.1 of the License, or (at  *
 *       your option) any later version. The text of the GNU Lesser      *
 *       General Public License is included with this library in the     *
 *       file LICENSE.TXT.                                               *
 *   (2) The BSD-style license that is included with this library in     *
 *       the file ODE-LICENSE-BSD.TXT and ODE4J-LICENSE-BSD.TXT.         *
 *                                                                       *
 * This library is distributed in the hope that it will be useful,       *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the files    *
 * LICENSE.TXT, ODE-LICENSE-BSD.TXT and ODE4J-LICENSE-BSD.TXT for more   *
 * details.                                                              *
 *                                                                       *
 *************************************************************************/
package org.ode4j.ode;

import cn.solarmoon.spark_core.phys.SparkMathKt;
import kotlin.Pair;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;

import java.util.ArrayList;
import java.util.List;

public interface DBox extends DGeom {

	/**
	 * 复制一个只留存基本几何数据的Box
	 */
	default DBox baseCopy() {
		var box = OdeHelper.createBox(getLengths().copy());
		box.setPosition(getPosition().copy());
		box.setRotation(getRotation().copy());
		return box;
	}

	/**
	 * @return 获取当前box的八个顶点
	 */
	default List<Vector3d> getVertexes() {
		List<Vector3d> vertices = new ArrayList<>(8);
		var halfLengths = SparkMathKt.toVector3d(getLengths()).div(2.0);

		// 初始化 vertices
		for (int i = 0; i < 8; i++) {
			vertices.add(new Vector3d());
		}

		// 计算每个顶点的相对位置
		for (int i = 0; i < 8; i++) {
			Vector3d relativePos = new Vector3d(
					(i & 1) == 1 ? halfLengths.x : -halfLengths.x,
					(i & 2) == 2 ? halfLengths.y : -halfLengths.y,
					(i & 4) == 4 ? halfLengths.z : -halfLengths.z
			);

			DVector3 realPos = new DVector3();
			getRelPointPos(relativePos.x, relativePos.y, relativePos.z, realPos);
			vertices.set(i, SparkMathKt.toVector3d(realPos));
		}

		return vertices;
	}

	/**
	 * @return 以每边为单位按顺序获取定点组（也就是十二条边对应的十二组顶点）
	 */
	default List<Vector3d> getOrderedVertexes() {
		var vertices = getVertexes();
		// 按照指定顺序重排顶点
		return List.of(
                vertices.get(0), vertices.get(1),
                vertices.get(0), vertices.get(2),
                vertices.get(0), vertices.get(4),
                vertices.get(6), vertices.get(2),
                vertices.get(6), vertices.get(4),
                vertices.get(6), vertices.get(7),
                vertices.get(3), vertices.get(1),
                vertices.get(3), vertices.get(2),
                vertices.get(3), vertices.get(7),
                vertices.get(5), vertices.get(1),
                vertices.get(5), vertices.get(4),
                vertices.get(5), vertices.get(7)
		);
	}
	
	/**
	 * Set the side lengths of the given box.
	 *
	 * @param lx      the length of the box along the X axis
	 * @param ly      the length of the box along the Y axis
	 * @param lz      the length of the box along the Z axis
	 *
	 * @see #getLengths()
	 */
	void setLengths (double lx, double ly, double lz);
	

	/**
	 * Get the side lengths of a box.
	 *
	 * @param result  the returned side lengths
	 *
	 * @see #setLengths(DVector3C)
	 */
	void getLengths (DVector3 result);
	

	/**
	 * Set the side lengths of the given box.
	 *
	 * @param sides   the lengths of the box along the X, Y and Z axes
	 *
	 * @see #getLengths()
	 */
	void setLengths (DVector3C sides);
	
	
	/**
	 * Get the side lengths of a box.
	 *
	 * @return The returned side lengths.
	 *
	 * @see #setLengths(DVector3C)
	 */
	DVector3C getLengths ();

	
	/**
	 * Return the depth of a point in a box.
	 *
	 * @param p    the X, Y and Z coordinates of the point to test.
	 *
	 * @return The depth of the point. Points inside the box will have a
	 * positive depth, points outside it will have a negative depth, and points
	 * on the surface will have a depth of zero.
	 */
	double getPointDepth(DVector3C p);

}
