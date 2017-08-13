package com.isha.routes;

public class SigninCredentials {
	
	private String email;
	private String passwd;
	private String provider;
	
	public SigninCredentials() {
		super();
		// TODO Auto-generated constructor stub
	}

	public SigninCredentials(String email, String passwd, String provider) {
		super();
		this.email = email;
		this.passwd = passwd;
		this.provider = provider;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswd() {
		return passwd;
	}

	public void setPasswd(String passwd) {
		this.passwd = passwd;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}
	
	
	

}
