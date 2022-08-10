package com.valschema.myschemavalidator

import cats.effect.Sync
import com.valschema.myschemavalidator.algebras.KVStore

import java.io.{File, PrintWriter}
import java.nio.file.Paths
import scala.io.{Codec, Source}

object FolderKVStore {

  def apply[F[_]: Sync](folderPath: String): KVStore[F, String, String] =
    new KVStore[F, String, String] {
      private def fileName(name: String): String = Paths.get(folderPath, name).toString

      def get(key: String): F[Option[String]] =
        Sync[F].delay {
          val name = fileName(key)
          Option.when(new File(name).exists()) {
            val buf = Source.fromFile(name)(Codec("UTF-8"))
            try { buf.getLines().mkString } finally buf.close()
          }
        }

      def put(key: String, value: String): F[Unit] =
        Sync[F].delay {
          val pw = new PrintWriter(new File(fileName(key)))
          try { pw.write(value) } finally pw.close()
        }

      def list: F[List[String]] =
        Sync[F].delay {
          val dm = new File(folderPath)
          if (dm.exists() && dm.isDirectory) {
            dm.listFiles().toList.filter(_.isDirectory == false).map(_.getName)
          } else throw new RuntimeException(s"Cannot list files. Inexistent folder: $folderPath!")
        }
    }


}
