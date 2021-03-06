// TxBits - An open source Bitcoin and crypto currency exchange
// Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package models

import play.api.db.DB
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import play.api.Play
import java.sql.Timestamp
import org.joda.time.DateTime
import securesocial.core.{ Token, SocialUser }
import service.{ PGP, TOTPSecret }
import play.api.libs.json.Json
import java.security.SecureRandom
import anorm.JodaParameterMetaData._

class UserModel(val db: String = "default") {

  def create(email: String, password: String, onMailingList: Boolean, pgp: Option[String], token: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select create_user_complete as id from create_user_complete($email, $password, $onMailingList, $pgp, $token)
    """.map(row => row[Option[Long]]("id")).list.head
  }

  // insecure version, usable only in tests
  def create(email: String, password: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    SQL"""
    select create_user as id from create_user($email, $password, $onMailingList, null, 'en')
    """.map(row => row[Long]("id")).list.headOption
  }

  def findUserById(id: Long): Option[SocialUser] =
    DB.withConnection(db) { implicit c =>
      SQL"""select * from find_user_by_id($id)"""().map(row =>
        new SocialUser(
          row[Long]("id"),
          row[String]("email"),
          row[Int]("verification"),
          row[String]("language"),
          row[Boolean]("on_mailing_list"),
          row[Boolean]("tfa_enabled"),
          row[Option[String]]("pgp"),
          row[Boolean]("manualauto_mode"),
          row[Option[String]]("user_country"),
          row[Boolean]("docs_verified"),
          row[Option[String]]("partner"),
          row[Option[String]]("admin_xx")
        )
      ).headOption
    }

  def userExists(email: String): Boolean = DB.withConnection(db) { implicit c =>
    SQL"select * from user_exists($email)"().map(row =>
      row[Boolean]("user_exists")
    ).head
  }

  def userHasTotp(email: String): Boolean = DB.withConnection(db) { implicit c =>
    SQL"select * from user_has_totp($email)"().map(row =>
      row[Option[Boolean]]("user_has_totp").getOrElse(false)
    ).head
  }

  def totpLoginStep1(email: String, password: String, user_country: String, browserHeaders: String, ip: String): Option[String] = DB.withConnection(db) { implicit c =>
    SQL"""
     select totp_login_step1($email, $password, $user_country, $browserHeaders, inet($ip))
    """().map(row =>
      row[Option[String]]("totp_login_step1")
    ).head
  }

  def totpLoginStep2(email: String, totpHash: String, totpToken: String, browserHeaders: String, ip: String): Option[SocialUser] = DB.withConnection(db) { implicit c =>
    SQL"""
    select * from totp_login_step2($email, $totpHash, ${safeToInt(totpToken)}, $browserHeaders, inet($ip))
    """().map(row => (row[Option[Long]]("id"),
      row[Option[String]]("email"),
      row[Option[Int]]("verification"),
      row[Option[Boolean]]("on_mailing_list"),
      row[Option[Boolean]]("tfa_enabled"),
      row[Option[String]]("pgp"),
      row[String]("language"),
      row[Option[Boolean]]("manualauto_mode"),
      row[Option[String]]("user_country"),
      row[Option[Boolean]]("docs_verified"),
      row[Option[String]]("partner")) match {
        case (Some(id: Long),
          Some(email: String),
          Some(verification: Int),
          Some(on_mailing_list: Boolean),
          Some(tfa_enabled: Boolean),
          pgp: Option[String],
          language: String,
          manualauto_mode: Option[Boolean],
          user_country: Option[String],
          docs_verified: Option[Boolean],
          partner: Option[String]) =>
          Some(SocialUser(id, email, verification, language, on_mailing_list, tfa_enabled, pgp, manualauto_mode.getOrElse(false), user_country, docs_verified.getOrElse(false), partner))
        case _ =>
          None
      }
    ).head
  }

  def findUserByEmailAndPassword(email: String, password: String, user_country: String, browserHeaders: String, ip: String): Option[SocialUser] = DB.withConnection(db) { implicit c =>
    SQL"""
    select * from find_user_by_email_and_password($email, $password, $user_country, $browserHeaders, inet($ip))
    """().map(row => (row[Option[Long]]("id"),
      row[Option[String]]("email"),
      row[Option[Int]]("verification"),
      row[Option[Boolean]]("on_mailing_list"),
      row[Option[Boolean]]("tfa_enabled"),
      row[Option[String]]("pgp"),
      row[Option[String]]("language"),
      row[Option[Boolean]]("manualauto_mode"),
      row[Option[String]]("user_country"),
      row[Option[Boolean]]("docs_verified"),
      row[Option[String]]("partner")) match {
        case (Some(id: Long),
          Some(email: String),
          Some(verification: Int),
          Some(on_mailing_list: Boolean),
          Some(tfa_enabled: Boolean),
          pgp: Option[String],
          Some(language: String),
          Some(manualauto_mode: Boolean),
          user_country: Option[String],
          Some(docs_verified: Boolean),
          partner: Option[String]) =>
          Some(SocialUser(id, email, verification, language, on_mailing_list, tfa_enabled, pgp, manualauto_mode, user_country, docs_verified, partner))
        case _ =>
          None
      }
    ).head
  }

  def findToken(token: String): Option[Token] = DB.withConnection(db) { implicit c =>
    SQL"""
    select * from find_token($token)
    """().map(row =>
      Token(token, row[String]("email"), row[DateTime]("creation"), row[DateTime]("expiration"), row[Boolean]("is_signup"), row[String]("language"))
    ).headOption
  }

  def deleteToken(token: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select * from delete_token($token)
    """.execute()
  }

  def deleteExpiredTokens() = DB.withConnection(db) { implicit c =>
    SQL"""select * from delete_expired_tokens()""".execute()
  }

  def deleteExpiredTOTPBlacklistTokens() = DB.withConnection(db) { implicit c =>
    SQL"""
    select * from delete_expired_totp_blacklist_tokens()
    """.execute()
  }

  def saveUser(id: Long, email: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    SQL"select * from update_user($id, $email, $onMailingList)".execute()
  }

  def userChangePass(id: Long, oldPassword: String, newPassword: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select user_change_password($id, $oldPassword, $newPassword)
    """().map(row =>
      row[Boolean]("user_change_password")
    ).head
  }

  def userResetPassComplete(email: String, token: String, password: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select user_reset_password_complete as success from user_reset_password_complete($email, $token, $password)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def trustedActionStart(email: String, isSignup: Boolean, language: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select trusted_action_start as success from trusted_action_start($email, $isSignup, $language)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def genTFASecret(uid: Long) = DB.withConnection(db) { implicit c =>
    val secret = TOTPSecret()
    val random = new SecureRandom
    // build a string that will be parsed into an array in the postgres function
    // generate a 6 digit random number that doesn't start with a 0
    val otps = Seq.fill(10)((random.nextInt(9) + 1).toString concat "%05d".format(random.nextInt(100000)))
    // everything is off by default
    val success = SQL"""
    select update_tfa_secret as success from update_tfa_secret($uid, ${secret.toBase32}, ${otps.mkString(",")})
    """().map(row =>
      row[Boolean]("success")
    ).head
    if (success) {
      Some(secret, otps)
    } else {
      None
    }
  }

  def turnOffTFA(uid: Long, tfa_code: String, password: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select turnoff_tfa as success from turnoff_tfa($uid, ${safeToInt(tfa_code)}, $password)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def addPGP(uid: Long, password: String, tfa_code: Option[String], pgp: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select user_add_pgp as success from user_add_pgp($uid, $password, ${optStrToInt(tfa_code)}, $pgp)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def removePGP(uid: Long, password: String, tfa_code: Option[String]) = DB.withConnection(db) { implicit c =>
    SQL"""
    select user_remove_pgp as success from user_remove_pgp($uid, $password, ${optStrToInt(tfa_code)};
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def turnOnTFA(uid: Long, tfa_code: String, password: String) = DB.withConnection(db) { implicit c =>
    SQL"""
     select turnon_tfa as success from turnon_tfa($uid, ${safeToInt(tfa_code)}, $password)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def turnOffEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    SQL"""
    select turnoff_emails as success from turnoff_emails($uid)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def turnOnEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    SQL"""
    select turnon_emails as success from turnon_emails($uid)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def changeLanguage(uid: Long, language: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select change_language as success from change_language($uid, $language)
    """().map(row =>
      row[Boolean]("success")
    ).head
  }

  def userPgpByEmail(email: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    select * from user_pgp_by_email($email)
    """().map(row =>
      row[Option[String]]("pgp")
    ).head
  }

  private def optStrToInt(optStr: Option[String]) = {
    safeToInt(optStr.getOrElse(""))
  }

  private def safeToInt(str: String) = {
    try {
      str.toInt
    } catch {
      case _: Throwable => 0
    }
  }

  def create_order(uid: Long, country_id: String, order_type: Option[String], status: Option[String], partner: Option[String], currency: String, initial_value: Option[BigDecimal], local_fee: Option[BigDecimal], global_fee: Option[BigDecimal], bank: Option[String], agency: Option[String], account: Option[String], doc1: Option[String]) = DB.withConnection(db) { implicit c =>
    SQL"""
     select create_order as success from create_order($uid, $country_id, ${order_type.get}, ${status.get}, ${partner.get}, $currency, ${initial_value.get}, ${local_fee.get}, ${global_fee.get}, ${bank}, ${agency}, ${account}, ${doc1})
    """.execute()
  }

  def create_order_with_picture(uid: Long, country_id: String, order_type: String, status: String, partner: String, currency: String, initial_value: BigDecimal, local_fee: BigDecimal, global_fee: BigDecimal, bank: String, agency: String, account: String, doc1: String, image_id: Long) = DB.withConnection(db) { implicit c =>
    SQL"""
     select create_order as success from create_order($uid, $country_id, $order_type, $status, $partner, $currency, $initial_value, $local_fee, $global_fee, ${bank}, ${agency}, ${account}, ${doc1}, ${image_id})
    """.execute()
  }

  def update_user_doc(uid: Long, docNumber: String, image_id: Long, fileName: String) = DB.withConnection(db) { implicit c =>
    SQL"""
     select update_user_doc as success from update_user_doc($uid, $docNumber, $image_id, $fileName)
    """.execute()
  }

  def update_order(order_id: Long, status: String, net_value: BigDecimal, comment: String, local_fee: BigDecimal, global_fee: BigDecimal, admin_id: Long) = DB.withConnection(db) { implicit c =>
    SQL"""
     select update_order as success from update_order($order_id, $status, $net_value, $comment, $local_fee, $global_fee, $admin_id)
    """.execute()
  }

  def update_order_with_picture(order_id: Long, order_type: String, status: String, processed_value: BigDecimal, local_fee: BigDecimal, global_fee: BigDecimal, comment: String, image_id: Long, admin_id: Long) = DB.withConnection(db) { implicit c =>
    SQL"""
     select update_order_with_picture as success from update_order_with_picture($order_id, $order_type, $status, $processed_value, $local_fee, $global_fee, $comment, ${image_id}, $admin_id)
    """.execute()
  }

  def update_personal_info(uid: Long, first_name: Option[String], middle_name: Option[String], last_name: Option[String], doc1: Option[String], doc2: Option[String], doc3: Option[String], doc4: Option[String], doc5: Option[String], bank: Option[String], agency: Option[String], account: Option[String], partner: Option[String], partner_account: Option[String], manualauto_mode: Option[Boolean]) = DB.withConnection(db) { implicit c =>
    val middle_name_s = middle_name.getOrElse("")
    val doc1_s = doc1.getOrElse("")
    val doc2_s = doc2.getOrElse("")
    val doc3_s = doc3.getOrElse("")
    val doc4_s = doc4.getOrElse("")
    val doc5_s = doc5.getOrElse("")
    val bank_s = bank.getOrElse("")
    val agency_s = agency.getOrElse("")
    val account_s = account.getOrElse("")
    val partner_s = partner.getOrElse("")
    val partner_account_s = partner_account.getOrElse("")
    val manualauto_mode_b = manualauto_mode.getOrElse(false)
    SQL"""
     select update_personal_info as success from update_personal_info($uid, ${first_name.get}, ${middle_name_s}, ${last_name.get}, ${doc1_s}, ${doc2_s}, ${doc3_s}, ${doc4_s}, ${doc5_s}, ${bank_s}, ${agency_s}, ${account_s}, ${partner_s}, ${partner_account_s}, ${manualauto_mode_b})
    """.execute()
  }

  def update_bank_data(uid: Long, bank: Option[String], agency: Option[String], account: Option[String]) = DB.withConnection(db) { implicit c =>
    val bank_s = bank.getOrElse("")
    val agency_s = agency.getOrElse("")
    val account_s = account.getOrElse("")
    SQL"""
     select update_bank_data as success from update_bank_data($uid, ${bank_s}, ${agency_s}, ${account_s})
    """.execute()
  }

  def change_manualauto(uid: Long, manualauto_mode: Option[Boolean]) = DB.withConnection(db) { implicit c =>
    SQL"""
     select change_manualauto as success from change_manualauto($uid, ${manualauto_mode.get})
    """.execute()
  }

  def save_admins(country: Option[String], admin_g1: Option[String], admin_g2: Option[String], admin_l1: Option[String], admin_l2: Option[String], admin_o1: Option[String], admin_o2: Option[String]) = DB.withConnection(db) { implicit c =>
    SQL"""
     select save_admins as success from save_admins(${country.get}, ${admin_g1.get}, ${admin_g2.get}, ${admin_l1.get}, ${admin_l2.get}, ${admin_o1.get}, ${admin_o2.get})
    """.execute()
  }

  def search(column: Option[String], value: String) = DB.withConnection(db) { implicit c =>
    var SQLTEST = ""
    if (column == "status") {
      SQL"""SELECT order_id, user_id, country_id, order_type, status, partner, created, currency, initial_value, total_fee, doc1, doc2, bank, agency, account, closed, processed_by, net_value, comment, key1, key2, image_id
       FROM public.orders where status = $value"""().map(row => (
        row[Long]("order_id"),
        row[Long]("user_id")
      )).toList
    } else {
      SQL"""SELECT order_id, user_id, country_id, order_type, status, partner, created, currency, initial_value, total_fee, doc1, doc2, bank, agency, account, closed, processed_by, net_value, comment, key1, key2, image_id
       FROM public.orders where status = $value"""().map(row => (
        row[Long]("order_id"),
        row[Long]("user_id")
      )).toList
    }

  }
}
