import java.io.{File, FileOutputStream}
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

import scala.collection.mutable

import sbt._

object ScaladocAssets {
  private val AssetAttributes = Map(
    "audio" -> Set("src"),
    "embed" -> Set("src"),
    "image" -> Set("href", "xlink:href"),
    "img" -> Set("src", "srcset"),
    "input" -> Set("src"),
    "link" -> Set("href"),
    "object" -> Set("data"),
    "script" -> Set("src"),
    "source" -> Set("src", "srcset"),
    "track" -> Set("src"),
    "use" -> Set("href", "xlink:href"),
    "video" -> Set("poster", "src")
  )

  private val AssetLinkRels = Set(
    "apple-touch-icon",
    "icon",
    "manifest",
    "mask-icon",
    "modulepreload",
    "preload",
    "stylesheet"
  )

  private val StartTag = """(?is)<([a-z][\w:-]*)\b([^>]*)>""".r
  private val Attribute = """(?is)\b([a-zA-Z_:][\w:.-]*)\s*=\s*["']([^"']*)["']""".r
  private val CssUrl = """(?i)url\(\s*["']?((?:https?:)?//[^)"']+)["']?\s*\)""".r

  def vendor(scaladocDir: File, log: Logger): File = {
    val files = ((scaladocDir ** "*.html").get ++ (scaladocDir ** "*.css").get).distinct
    val fileUrls = files.map(file => file -> findAssetUrls(file)).toMap
    val urls = fileUrls.values.flatten.toSet.toSeq.sorted
    val assetsDir = scaladocDir / "assets"

    IO.createDirectory(assetsDir)

    val localPaths = urls.map { url =>
      val destination = assetsDir / localFilename(url)
      if (!destination.exists || destination.length == 0) {
        download(url, destination, log)
      }
      url -> destination
    }.toMap

    fileUrls.foreach { case (file, fileAssetUrls) =>
      var content = read(file)
      fileAssetUrls.toSeq.sortBy(url => -url.length).foreach { url =>
        val relativePath = relativize(file.getParentFile, localPaths(url))
        content = content.replace(url, relativePath)
      }
      write(file, content)
    }

    val remaining = files.flatMap(findAssetUrls).toSet
    if (remaining.nonEmpty) {
      sys.error(
        "External Scaladoc assets remain after rewriting:\n" +
          remaining.toSeq.sorted.map(url => s"  $url").mkString("\n")
      )
    }

    log.info(s"Vendored ${urls.size} external Scaladoc assets into ${assetsDir.getPath}")
    assetsDir
  }

  private def findAssetUrls(file: File): Set[String] = {
    val content = read(file)
    val urls = mutable.Set.empty[String]

    if (file.getName.endsWith(".html")) {
      StartTag.findAllMatchIn(content).foreach { tagMatch =>
        val tag = tagMatch.group(1).toLowerCase
        val attributes = Attribute
          .findAllMatchIn(tagMatch.group(2))
          .map(attribute => attribute.group(1).toLowerCase -> attribute.group(2))
          .toMap
        val assetAttributes =
          if (tag == "link") {
            val rels = attributes.getOrElse("rel", "").toLowerCase.split("\\s+").toSet
            if (rels.intersect(AssetLinkRels).nonEmpty) AssetAttributes(tag)
            else Set.empty[String]
          } else AssetAttributes.getOrElse(tag, Set.empty)

        assetAttributes.foreach { attributeName =>
          attributes.get(attributeName).foreach { value =>
            val candidates = if (attributeName == "srcset") value.split(",") else Array(value)
            candidates.foreach { candidate =>
              val url = if (attributeName == "srcset") candidate.trim.split("\\s+", 2).head else candidate
              if (isExternalUrl(url)) urls += url.trim
            }
          }
        }

        attributes.get("style").foreach { style =>
          CssUrl.findAllMatchIn(style).foreach(matchResult => urls += matchResult.group(1).trim)
        }
      }
    }

    CssUrl.findAllMatchIn(content).foreach(matchResult => urls += matchResult.group(1).trim)
    urls.filter(isExternalUrl).toSet
  }

  private def isExternalUrl(value: String): Boolean = {
    val url = value.trim
    url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")
  }

  private def normalizedUrl(url: String): String =
    if (url.startsWith("//")) s"https:$url" else url

  private def localFilename(url: String): String = {
    val path = new URI(normalizedUrl(url)).getPath
    val decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name())
    val rawName = decodedPath.split("/").lastOption.filter(_.nonEmpty).getOrElse("asset")
    val safeName = rawName.replaceAll("[^A-Za-z0-9._-]", "-")
    val extensionIndex = safeName.lastIndexOf('.')
    val (stem, extension) =
      if (extensionIndex > 0) (safeName.take(extensionIndex), safeName.drop(extensionIndex))
      else (safeName, ".bin")
    val digest = MessageDigest
      .getInstance("SHA-256")
      .digest(url.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString
      .take(12)
    s"$stem-$digest$extension"
  }

  private def download(url: String, destination: File, log: Logger): Unit = {
    val normalized = normalizedUrl(url)
    log.info(s"Downloading $normalized")
    val connection = new URI(normalized).toURL.openConnection()
    connection.setConnectTimeout(60000)
    connection.setReadTimeout(60000)
    connection.setRequestProperty("User-Agent", "mat-scaladoc-assets/1.0")
    val input = connection.getInputStream
    val output = new FileOutputStream(destination)
    try {
      val buffer = new Array[Byte](8192)
      var readBytes = input.read(buffer)
      while (readBytes != -1) {
        output.write(buffer, 0, readBytes)
        readBytes = input.read(buffer)
      }
    } finally {
      input.close()
      output.close()
    }
  }

  private def relativize(from: File, to: File): String =
    from.toPath
      .toAbsolutePath
      .relativize(to.toPath.toAbsolutePath)
      .toString
      .replace(File.separatorChar, '/')

  private def read(file: File): String =
    new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)

  private def write(file: File, content: String): Unit =
    Files.write(file.toPath, content.getBytes(StandardCharsets.UTF_8))
}
