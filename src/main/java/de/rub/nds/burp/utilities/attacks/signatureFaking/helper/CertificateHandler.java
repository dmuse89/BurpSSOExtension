/**
 * EsPReSSO - Extension for Processing and Recognition of Single Sign-On Protocols.
 * Copyright (C) 2015 Tim Guenther and Christian Mainka
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package de.rub.nds.burp.utilities.attacks.signatureFaking.helper;

import de.rub.nds.burp.utilities.Logging;
import de.rub.nds.burp.utilities.attacks.signatureFaking.exceptions.CertificateHandlerException;
import java.io.*;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import org.apache.commons.codec.binary.Base64;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

/**
 * @author Juraj Somorovsky - juraj.somorovsky@rub.de
 */
public class CertificateHandler
{

    private X509Certificate certificate;

    private PublicKey originalPublicKey;

    private X509CertImpl fakedCertificate;

    private KeyPair fakedKeyPair;

    private CertificateFactory certFactory;

    public CertificateHandler( final String cert )
        throws CertificateHandlerException
    {
        try
        {
            certFactory = CertificateFactory.getInstance( "X.509" );
            certificate =
                (X509Certificate) certFactory.generateCertificate( new ByteArrayInputStream( Base64.decodeBase64( cert ) ) );
            originalPublicKey = certificate.getPublicKey();
        }
        catch ( CertificateException e )
        {
            throw new CertificateHandlerException( e );
        }
    }

    public void createFakedCertificate()
        throws CertificateHandlerException
    {
        try
        {
            Logging.getInstance().log(getClass(), "Faking the found certificate", Logging.DEBUG);
            // TODO: implement this with bouncy castle
            KeyPairGenerator kpg = KeyPairGenerator.getInstance( originalPublicKey.getAlgorithm() );
            kpg.initialize( ( (RSAPublicKey) certificate.getPublicKey() ).getModulus().bitLength() );
            fakedKeyPair = kpg.generateKeyPair();

            X509CertInfo info = new X509CertInfo();
            CertificateValidity interval =
                new CertificateValidity( certificate.getNotBefore(), certificate.getNotAfter() );
            // TODO: new SecureRandom().generateSeed(64) is very slow! Replace
            // it?
            // BigInteger sn = new BigInteger(new
            // SecureRandom().generateSeed(64));
            BigInteger sn = new BigInteger( 64, new Random() );
            X500Name owner = new X500Name( certificate.getSubjectDN().getName() );
            X500Name issuer = new X500Name( certificate.getIssuerDN().getName() );

            info.set( X509CertInfo.VALIDITY, interval );
            info.set( X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber( sn ) );
            // API Change in Java7 vs Java8
            try
            {
                info.set( X509CertInfo.SUBJECT, new CertificateSubjectName( owner ) );
                info.set( X509CertInfo.ISSUER, new CertificateIssuerName( issuer ) );
            }
            catch ( Exception e )
            {
                info.set( X509CertInfo.SUBJECT, owner );
                info.set( X509CertInfo.ISSUER, issuer );
            }
            info.set( X509CertInfo.KEY, new CertificateX509Key( fakedKeyPair.getPublic() ) );

            info.set( X509CertInfo.VERSION, new CertificateVersion( CertificateVersion.V3 ) );

            AlgorithmId algo = new AlgorithmId( new ObjectIdentifier( certificate.getSigAlgOID() ) );
            info.set( X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId( algo ) );

            // Sign the cert to identify the algorithm that's used.
            fakedCertificate = new X509CertImpl( info );
            fakedCertificate.sign( fakedKeyPair.getPrivate(), certificate.getSigAlgName() );
        }
        catch ( CertificateException | IOException | InvalidKeyException | NoSuchAlgorithmException
                | NoSuchProviderException | SignatureException e )
        {
            throw new CertificateHandlerException( e );
        }
    }

    public PublicKey getOriginalPublicKey()
    {
        return originalPublicKey;
    }

    public void setOriginalPublicKey( PublicKey originalPublicKey )
    {
        this.originalPublicKey = originalPublicKey;
    }

    public X509CertImpl getFakedCertificate()
    {
        return fakedCertificate;
    }

    public void setFakedCertificate( X509CertImpl fakedCertificate )
    {
        this.fakedCertificate = fakedCertificate;
    }

    public KeyPair getFakedKeyPair()
    {
        return fakedKeyPair;
    }

    public void setFakedKeyPair( KeyPair fakedKeyPair )
    {
        this.fakedKeyPair = fakedKeyPair;
    }

    public String getFakedCertificateString()
        throws CertificateHandlerException
    {
        try
        {
            return new String( Base64.encodeBase64( fakedCertificate.getEncoded() ) );
        }
        catch ( CertificateEncodingException e )
        {
            throw new CertificateHandlerException( e );
        }
    }
}
