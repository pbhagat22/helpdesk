package com.example.helpdesk.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.helpdesk.user.UserRepository;

// Spring Security calls this during login to find the user by the username (email)
// they typed. It then compares the typed password with the stored hash itself.
@Service
public class HelpdeskUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public HelpdeskUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return users.findByEmail(email)
			.map(HelpdeskUserDetails::from)
			.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
	}

}
