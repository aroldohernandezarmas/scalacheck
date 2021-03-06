/*-------------------------------------------------------------------------*\
 **  ScalaCheck                                                             **
 **  Copyright (c) 2007-2015 Rickard Nilsson. All rights reserved.          **
 **  http://www.scalacheck.org                                              **
 **                                                                         **
 **  This software is released under the terms of the Revised BSD License.  **
 **  There is NO WARRANTY. See the file LICENSE for the full text.          **
 \*------------------------------------------------------------------------ */

package org.scalacheck

import language.higherKinds
import language.implicitConversions

import rng.Seed

sealed trait Cogen[-T] {

  def perturb(seed: Seed, t: T): Seed

  def cogen[A](t: T, g: Gen[A]): Gen[A] =
    Gen.gen((p, seed) => g.doApply(p, perturb(seed, t)))

  def contramap[S](f: S => T): Cogen[S] =
    Cogen((seed, s) => perturb(seed, f(s)))
}

object Cogen extends CogenArities {

  def apply[T](implicit ev: Cogen[T]): Cogen[T] = ev

  def apply[T](f: T => Long): Cogen[T] =
    new Cogen[T] {
      def perturb(seed: Seed, t: T): Seed = seed.reseed(f(t))
    }

  def apply[T](f: (Seed, T) => Seed): Cogen[T] =
    new Cogen[T] {
      def perturb(seed: Seed, t: T): Seed = f(seed, t).next
    }

  def it[T, U](f: T => Iterator[U])(implicit U: Cogen[U]): Cogen[T] =
    new Cogen[T] {
      def perturb(seed: Seed, t: T): Seed =
        f(t).foldLeft(seed)(U.perturb).next
    }

  def perturb[A](seed: Seed, a: A)(implicit A: Cogen[A]): Seed =
    A.perturb(seed, a)

  implicit lazy val cogenUnit: Cogen[Unit] =
    Cogen(_ => 0L)

  // implicit lazy val cogenBoolean: Cogen[Boolean] =
  //   Cogen((seed, b) => if (b) seed.next else seed)
  implicit lazy val cogenBoolean: Cogen[Boolean] =
    Cogen(b => if (b) 1L else 0L)

  implicit lazy val cogenByte: Cogen[Byte] = Cogen(_.toLong)
  implicit lazy val cogenShort: Cogen[Short] = Cogen(_.toLong)
  implicit lazy val cogenChar: Cogen[Char] = Cogen(_.toLong)
  implicit lazy val cogenInt: Cogen[Int] = Cogen(_.toLong)
  implicit lazy val cogenLong: Cogen[Long] = Cogen(n => n)

  implicit lazy val cogenFloat: Cogen[Float] =
    Cogen(n => java.lang.Float.floatToRawIntBits(n).toLong)

  implicit lazy val cogenDouble: Cogen[Double] =
    Cogen(n => java.lang.Double.doubleToRawLongBits(n))

  implicit def cogenOption[A](implicit A: Cogen[A]): Cogen[Option[A]] =
    Cogen((seed, o) => o.fold(seed)(a => A.perturb(seed.next, a)))

  implicit def cogenEither[A, B](implicit A: Cogen[A], B: Cogen[B]): Cogen[Either[A, B]] =
    Cogen((seed, e) => e.fold(a => A.perturb(seed, a), b => B.perturb(seed.next, b)))

  implicit def cogenArray[A](implicit A: Cogen[A]): Cogen[Array[A]] =
    Cogen((seed, as) => perturbArray(seed, as))

  implicit def cogenString: Cogen[String] =
    Cogen.it(_.iterator)

  implicit def cogenList[A: Cogen]: Cogen[List[A]] =
    Cogen.it(_.iterator)

  implicit def cogenVector[A: Cogen]: Cogen[Vector[A]] =
    Cogen.it(_.iterator)

  implicit def cogenSet[A: Cogen: Ordering]: Cogen[Set[A]] =
    Cogen.it(_.toVector.sorted.iterator)

  implicit def cogenMap[K: Cogen: Ordering, V: Cogen: Ordering]: Cogen[Map[K, V]] =
    Cogen.it(_.toVector.sorted.iterator)

  def perturbPair[A, B](seed: Seed, ab: (A, B))(implicit A: Cogen[A], B: Cogen[B]): Seed =
    B.perturb(A.perturb(seed, ab._1), ab._2)

  def perturbArray[A](seed: Seed, as: Array[A])(implicit A: Cogen[A]): Seed = {
    var s = seed
    var i = 0
    while (i < as.length) { s = A.perturb(s, as(i)); i += 1 }
    s.next
  }
}
