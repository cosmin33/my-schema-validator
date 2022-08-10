package com.valschema.myschemavalidator.algebras

import cats.effect.MonadCancel
import cats.effect.std.Semaphore
import cats.implicits._
import cats.{~>, _}

trait KVStore[F[_], K, V] { self =>
  def get(key: K): F[Option[V]]
  def put(key: K, value: V): F[Unit]
  def list: F[List[K]]

  // methods for store composition

  def comapKey[K0](i: Inject[K0, K])(implicit M: Monad[F]): KVStore[F, K0, V] = new KVStore[F, K0, V] {
    def get(key: K0): F[Option[V]] = self.get(i.inj(key))
    def put(key: K0, value: V): F[Unit] = self.put(i.inj(key), value)
    def list: F[List[K0]] = self.list.map(_.mapFilter(i.prj))
  }

  def mapValue[V0](i: Inject[V0, V])(implicit M: Monad[F]): KVStore[F, K, V0] = new KVStore[F, K, V0] {
    def get(key: K): F[Option[V0]] = self.get(key).map(_.flatMap(i.prj))
    def put(key: K, value: V0): F[Unit] = self.put(key, i.inj(value))
    def list: F[List[K]] = self.list
  }

  /** transforms this KVStore into a new one changint it's container with a natural transformation */
  def mapK[F1[_]](fn: F ~> F1): KVStore[F1, K, V] = new KVStore[F1, K, V] {
    def get(key: K): F1[Option[V]] = fn(self.get(key))
    def put(key: K, value: V): F1[Unit] = fn(self.put(key, value))
    def list: F1[List[K]] = fn(self.list)
  }

  /** transforms this KVStore into a new one that asks a permit from a semaphore on every store operation */
  def withSemaphore(sem: Semaphore[F])(implicit M: MonadCancel[F, Throwable]): KVStore[F, K, V] =
    mapK(λ[F ~> F](fa => sem.permit.use(_ => fa)))

}
